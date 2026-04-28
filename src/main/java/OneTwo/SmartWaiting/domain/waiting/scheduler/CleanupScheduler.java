package OneTwo.SmartWaiting.domain.waiting.scheduler;

import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import OneTwo.SmartWaiting.domain.store.service.StoreStatisticService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final WaitingRepository waitingRepository;
    private final NotificationService notificationService;
    private final StoreStatisticService storeStatisticService;

    @Scheduled(cron = "0 0 4 * * ?")  // 매일 새벽 4시에 실행
    @Transactional
    public void cleanupGhostWaitings(){
        log.info("유령 웨이팅 정리 작업 시작");

        List<Waiting> ghostWaitings = waitingRepository.findAllByStatusIn(
                Arrays.asList(WaitingStatus.WAITING,WaitingStatus.CALL)
        );

        int cleanupCount = 0;

        for(Waiting waiting : ghostWaitings){
            waiting.changeStatus(WaitingStatus.CANCEL);

            notificationService.closeConnection(waiting.getMember().getId());

            String message = "영업 시간이 종료되어 대기가 자동 취소되었습니다. 이용에 불편을 드려 정말 죄송합니다.";
            notificationService.sendToClient(waiting.getMember().getId(), message);

            cleanupCount++;
        }

        log.info("유령 웨이팅 정리 작업 완료. 총 {}건 정리됨", cleanupCount);

        log.info("==== 식당별 통계 데이터 집계 시작 ====");

        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<Long> activeStoreIds = waitingRepository.findStoreIdsWithWaitingsSince(yesterday);

        for(Long storeId : activeStoreIds){
            try{
                storeStatisticService.calculateAndSaveStoreStats(storeId);
            }catch(Exception e){
                log.error("식당별 통계 데이터 집계 중 오류 발생. Store ID: {}, Error: {}", storeId, e.getMessage());
            }
        }

        log.info("모든 심야 배치 작업 완료");
    }
}
