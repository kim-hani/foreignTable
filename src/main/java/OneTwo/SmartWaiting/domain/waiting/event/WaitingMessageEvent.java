package OneTwo.SmartWaiting.domain.waiting.event;

/**
 * 특정 회원에게 보낼 웨이팅 SSE 메시지. 트랜잭션 커밋 후 {@link WaitingNotificationListener}가 발송한다.
 */
public record WaitingMessageEvent(Long memberId, String message) {
}
