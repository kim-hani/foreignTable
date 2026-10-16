package OneTwo.SmartWaiting.domain.notification.redis;

/**
 * 인스턴스 간 SSE 전달용 Redis 메시지.
 *
 * @param type     SEND(메시지 전달) / CLOSE(연결 종료)
 * @param memberId 대상 회원
 * @param data     SEND일 때 보낼 메시지 (CLOSE면 null)
 */
public record SseRedisMessage(Type type, Long memberId, String data) {

    public enum Type { SEND, CLOSE }

    public static SseRedisMessage send(Long memberId, String data) {
        return new SseRedisMessage(Type.SEND, memberId, data);
    }

    public static SseRedisMessage close(Long memberId) {
        return new SseRedisMessage(Type.CLOSE, memberId, null);
    }
}
