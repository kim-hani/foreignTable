package OneTwo.SmartWaiting.domain.waiting.event;

import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.notification.service.FcmService;
import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import OneTwo.SmartWaiting.domain.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * 웨이팅 상태 변경 알림을 트랜잭션 커밋 후에 발송한다.
 *
 * <p>트랜잭션 안에서 바로 보내면 (1) 커밋이 실패·롤백돼도 알림은 이미 나가고, (2) 커밋 전이라 다른 요청에서는
 * 바뀐 상태가 아직 보이지 않는 시점에 손님이 알림을 받는다. AFTER_COMMIT으로 확정된 변경에 대해서만 알린다.
 *
 * <p>예외 처리 주의: 커밋 이후라 예외를 던져도 롤백되지 않고 요청자에게 500만 돌아가므로,
 * 알림 실패는 로그만 남긴다 (BusinessException 규칙은 요청 처리 흐름에 적용되는 것).
 * 같은 트랜잭션에서 발행된 이벤트는 발행 순서대로 처리된다 (예: 안내 메시지 후 연결 종료).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingNotificationListener {

    static final String THIRD_IN_LINE_MESSAGE = "📢 대기 순번이 3번째입니다! 매장 앞에서 대기해 주세요.";
    static final String FIRST_IN_LINE_MESSAGE = "🚨 곧 입장하실 차례입니다! 매장 앞에 대기해 주세요.";

    private final WaitingRepository waitingRepository;
    private final NotificationService notificationService;
    private final FcmService fcmService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(WaitingMessageEvent event) {
        try {
            notificationService.sendToClient(event.memberId(), event.message());
        } catch (Exception e) {
            log.error("웨이팅 알림 발송 실패: 사용자 ID = {}", event.memberId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConnectionClose(WaitingConnectionCloseEvent event) {
        try {
            notificationService.closeConnection(event.memberId());
        } catch (Exception e) {
            log.error("SSE 연결 종료 실패: 사용자 ID = {}", event.memberId(), e);
        }
    }

    // 커밋된 최신 대기열을 새 트랜잭션에서 조회 (원래 트랜잭션은 이미 끝났으므로 REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onQueueChanged(WaitingQueueChangedEvent event) {
        try {
            if (event.notifyThird()) {
                findWaitingAt(event.storeId(), 2)
                        .ifPresent(third -> notificationService.sendToClient(third.getMember().getId(), THIRD_IN_LINE_MESSAGE));
            }
            if (event.notifyFirst()) {
                findWaitingAt(event.storeId(), 0).ifPresent(first -> {
                    Member member = first.getMember();
                    notificationService.sendToClient(member.getId(), FIRST_IN_LINE_MESSAGE);
                    fcmService.sendFirstInLinePush(member.getFcmToken(), event.storeId());
                });
            }
        } catch (Exception e) {
            log.error("대기열 변경 알림 실패: 가게 ID = {}", event.storeId(), e);
        }
    }

    // index번째(0부터) 대기 중인 웨이팅
    private Optional<Waiting> findWaitingAt(Long storeId, int index) {
        List<Waiting> content = waitingRepository.findByStoreIdAndStatusOrderByTicketTimeAsc(
                storeId, WaitingStatus.WAITING, PageRequest.of(index, 1)).getContent();
        return content.stream().findFirst();
    }
}
