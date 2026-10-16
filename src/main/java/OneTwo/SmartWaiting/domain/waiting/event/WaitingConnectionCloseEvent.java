package OneTwo.SmartWaiting.domain.waiting.event;

/**
 * 웨이팅이 종료(착석·취소·노쇼)된 회원의 SSE 연결 종료 요청. 트랜잭션 커밋 후 처리된다.
 */
public record WaitingConnectionCloseEvent(Long memberId) {
}
