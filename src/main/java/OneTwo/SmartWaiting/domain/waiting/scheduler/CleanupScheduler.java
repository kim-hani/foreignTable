package OneTwo.SmartWaiting.domain.waiting.scheduler;

import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final WaitingRepository waitingRepository;
    private final NotificationService notificationService;

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
    }
}
