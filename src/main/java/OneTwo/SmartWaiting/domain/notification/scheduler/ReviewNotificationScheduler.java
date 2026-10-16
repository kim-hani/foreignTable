package OneTwo.SmartWaiting.domain.notification.scheduler;

import OneTwo.SmartWaiting.common.scheduler.SchedulerLock;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationScheduler {

    private final WaitingRepository waitingRepository;
    private final FcmService fcmService;
    private final SchedulerLock schedulerLock;

    // 매분 실행되므로 1분보다 짧게 점유 → 같은 분에는 한 인스턴스만, 다음 분에는 다시 실행 가능
    private static final Duration LOCK_HOLD = Duration.ofSeconds(50);

    // 매분 0초마다 자동 실행 (1시간 전 입장객 찾기)
    @Scheduled(cron = "0 * * * * *")
    @Transactional(readOnly = true)
    public void sendReviewRequestNotifications() {
        if (!schedulerLock.tryAcquire("sendReviewRequestNotifications", LOCK_HOLD)) {
            return;
        }

        // 분 단위로 절삭해 실행 지연(ms)이 있어도 매분 구간이 빈틈·중복 없이 이어지도록 함
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = now.minusMinutes(61);
        LocalDateTime end = now.minusMinutes(60);

        // 실제 방문 기록이 있는 사용자들에게만 푸시 알림 전송
        List<Waiting> targets = waitingRepository.findAllWithMemberAndStoreByStatusAndUpdatedAtRange(
                WaitingStatus.SEATED, start, end
        );

        for (Waiting waiting : targets) {
            Member member = waiting.getMember();

            // 푸시 알림 전송!
            fcmService.sendReviewRequestPush(
                    member.getFcmToken(), // 유저 핸드폰 토큰
                    "식사는 맛있게 하셨나요? ", // 제목
                    "방문하셨던 '" + waiting.getStore().getName() + "' 의 소중한 리뷰를 남겨주세요! ⭐️",
                    waiting.getStore().getId() // 프론트엔드가 사용할 가게 ID 데이터
            );
        }
    }
}