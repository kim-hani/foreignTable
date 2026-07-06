package OneTwo.SmartWaiting.domain.support.config;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.rag.SupportManualDocumentMapper;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플리케이션 기동 시 기존 고객지원 매뉴얼 전체를 벡터 저장소에 재색인한다(최초 인덱싱).
 *
 * <p>ddl-auto=update 등으로 매뉴얼이 DB에 남아있는데 벡터 인덱스가 비어있는 경우를 대비.
 * 각 매뉴얼마다 "기존 벡터 삭제 후 추가"라 여러 번 실행해도 안전(멱등)하다.
 * 테스트 컨텍스트에는 임베딩 모델/DB가 없으므로 @Profile("!test")로 비활성화한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SupportRagIndexInitializer implements ApplicationRunner {

    private final SupportManualRepository supportManualRepository;
    private final VectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) {
        List<SupportManual> manuals = supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
        if (manuals.isEmpty()) {
            log.info("[SupportRAG] 재색인 대상 매뉴얼 없음 — 건너뜀");
            return;
        }

        for (SupportManual manual : manuals) {
            vectorStore.delete(SupportManualDocumentMapper.manualIdFilter(manual.getId()));
            vectorStore.add(List.of(SupportManualDocumentMapper.toDocument(manual)));
        }
        log.info("[SupportRAG] 매뉴얼 {}건 재색인 완료", manuals.size());
    }
}
