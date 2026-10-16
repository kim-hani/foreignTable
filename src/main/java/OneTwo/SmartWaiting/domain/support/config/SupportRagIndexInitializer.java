package OneTwo.SmartWaiting.domain.support.config;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import OneTwo.SmartWaiting.domain.support.rag.SupportManualIndexer;
import OneTwo.SmartWaiting.domain.support.repository.SupportManualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 뜰 때 살아있는 매뉴얼을 전부 한 번씩 다시 색인한다.
 *
 * ddl-auto=update 같은 이유로 매뉴얼은 DB에 있는데 벡터 인덱스만 비어있는 경우, 그리고
 * 평소 색인이 실패해서 빠진 벡터를 여기서 메꾼다. 실제 색인은 SupportManualIndexer.sync에
 * 맡긴다 — 재색인 로직을 한 곳에만 두려는 것. sync는 실패해도 예외를 안 던지니 한 건 삐끗해도
 * 나머지는 계속 돈다.
 *
 * 테스트 컨텍스트엔 임베딩 모델/DB가 없으니 @Profile("!test")로 꺼둔다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SupportRagIndexInitializer implements ApplicationRunner {

    private final SupportManualRepository supportManualRepository;
    private final SupportManualIndexer supportManualIndexer;

    @Override
    public void run(ApplicationArguments args) {
        List<SupportManual> manuals = supportManualRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
        if (manuals.isEmpty()) {
            log.info("[SupportRAG] 재색인 대상 매뉴얼 없음 — 건너뜀");
            return;
        }

        manuals.forEach(manual -> supportManualIndexer.sync(manual.getId()));
        log.info("[SupportRAG] 매뉴얼 {}건 재색인 완료", manuals.size());
    }
}
