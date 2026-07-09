package OneTwo.SmartWaiting.domain.support.rag;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.event.SupportManualIndexEvent;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 매뉴얼이 바뀌면 벡터 저장소도 맞춰주는 색인기.
 *
 * 핵심 1) AFTER_COMMIT — 원본 트랜잭션이 실제로 커밋된 뒤에만 돈다. 롤백나면 아예 안 타므로
 * 원본은 없는데 벡터만 남는 유령 벡터가 안 생긴다.
 *
 * 핵심 2) 재조회 — 이벤트가 준 옛날 값을 믿지 않고, 색인하는 순간 DB에서 매뉴얼을 다시 읽는다.
 * 그래서 같은 매뉴얼을 거의 동시에 두 번 고쳐도 항상 "지금 DB에 있는 최신 내용"이 색인된다.
 *
 * 색인이 실패하면(임베딩 API 장애 등) 그 자리에서 몇 번 더 시도한다. 그래도 안 되면 원본은 이미
 * 커밋된 뒤라 되돌릴 수 없으니 눈에 띄는 표식으로 로그만 남긴다. 이렇게 빠진 벡터는 재기동 때
 * SupportRagIndexInitializer가 다시 훑어서 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportManualIndexer {

    /** 색인 실패 로그에 붙이는 표식. 운영 알림/검색을 이걸로 건다. */
    private static final String INDEX_FAILED = "[SupportRAG][INDEX-FAILED]";
    private static final int MAX_ATTEMPTS = 3;

    private final SupportManualRepository supportManualRepository;
    private final VectorStore vectorStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SupportManualIndexEvent event) {
        sync(event.manualId());
    }

    /**
     * 매뉴얼 하나의 벡터를 DB 최신 상태에 맞춘다. 몇 번을 돌려도 결과는 같다(멱등).
     * 실패해도 예외를 던지지 않는다 — 이벤트 리스너와 재기동 백필 양쪽에서 안전하게 부르기 위함.
     */
    public void sync(Long manualId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                doSync(manualId);
                return;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("[SupportRAG] manualId={} 색인 실패, 바로 재시도 {}/{}", manualId, attempt, MAX_ATTEMPTS);
                    continue;
                }
                // 원본은 이미 저장됐다. 사람이 알아채거나 재기동 백필이 채우도록 표식만 남긴다.
                log.error("{} manualId={} 색인 {}회 모두 실패 (원본은 저장됨, 재기동 백필 필요)",
                        INDEX_FAILED, manualId, MAX_ATTEMPTS, e);
            }
        }
    }

    private void doSync(Long manualId) {
        // 지금 DB에 살아있으면 최신 내용으로 재색인, 지워졌으면 벡터도 제거
        supportManualRepository.findByIdAndIsDeletedFalse(manualId)
                .ifPresentOrElse(this::reindex, () -> removeFromIndex(manualId));
    }

    // 기존 벡터를 지우고 최신 내용으로 다시 넣는다.
    private void reindex(SupportManual manual) {
        vectorStore.delete(SupportManualDocumentMapper.manualIdFilter(manual.getId()));
        vectorStore.add(List.of(SupportManualDocumentMapper.toDocument(manual)));
    }

    private void removeFromIndex(Long manualId) {
        vectorStore.delete(SupportManualDocumentMapper.manualIdFilter(manualId));
    }
}
