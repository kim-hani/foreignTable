package OneTwo.SmartWaiting.domain.notification.redis;

import OneTwo.SmartWaiting.domain.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 채널("sse:notification")을 구독해, 이 인스턴스에 연결된 회원에게만 SSE를 전달하거나 연결을 종료한다.
 * 연결이 없는 인스턴스에서는 아무 일도 하지 않는다.
 *
 * <p>예외 처리 주의: Redis 리스너 스레드라 예외를 던져도 요청자에게 전파되지 않으므로 로그만 남긴다
 * (RedisChatSubscriber와 동일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

    private final NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SseRedisMessage sseMessage = objectMapper.readValue(message.getBody(), SseRedisMessage.class);
            switch (sseMessage.type()) {
                case SEND -> notificationService.deliverLocal(sseMessage.memberId(), sseMessage.data());
                case CLOSE -> notificationService.closeLocal(sseMessage.memberId());
            }
        } catch (Exception e) {
            log.error("[SSE] Redis 메시지 처리 실패", e);
        }
    }
}
