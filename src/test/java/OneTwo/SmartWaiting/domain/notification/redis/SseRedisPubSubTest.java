package OneTwo.SmartWaiting.domain.notification.redis;

import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SseRedisPubSubTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SseRedisPublisher publisher = new SseRedisPublisher(redisTemplate);
    private final SseRedisSubscriber subscriber = new SseRedisSubscriber(notificationService);

    // 발행한 JSON을 그대로 구독자에게 넘겨 왕복(직렬화 → 역직렬화 → 로컬 처리)을 검증
    private void roundTrip(SseRedisMessage message) throws Exception {
        publisher.publish(message);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(SseRedisPublisher.CHANNEL), json.capture());

        subscriber.onMessage(new DefaultMessage(
                SseRedisPublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                json.getValue().getBytes(StandardCharsets.UTF_8)), null);
    }

    @Test
    @DisplayName("SEND 메시지 - 발행된 메시지를 받은 인스턴스는 로컬 연결로 전달한다.")
    void send_RoundTrip_DeliversLocally() throws Exception {
        roundTrip(SseRedisMessage.send(7L, "📢 사장님이 호출하셨습니다!"));

        verify(notificationService).deliverLocal(7L, "📢 사장님이 호출하셨습니다!");
        verify(notificationService, never()).closeLocal(anyLong());
    }

    @Test
    @DisplayName("CLOSE 메시지 - 발행된 종료 요청을 받은 인스턴스는 로컬 연결을 종료한다.")
    void close_RoundTrip_ClosesLocally() throws Exception {
        roundTrip(SseRedisMessage.close(7L));

        verify(notificationService).closeLocal(7L);
        verify(notificationService, never()).deliverLocal(anyLong(), anyString());
    }

    @Test
    @DisplayName("잘못된 메시지 - 리스너 스레드에서 예외를 던지지 않고 무시한다.")
    void malformedMessage_IsIgnored() {
        DefaultMessage broken = new DefaultMessage(
                SseRedisPublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> subscriber.onMessage(broken, null)).doesNotThrowAnyException();
        verify(notificationService, never()).deliverLocal(anyLong(), anyString());
    }
}
