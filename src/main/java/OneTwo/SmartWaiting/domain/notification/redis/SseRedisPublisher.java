package OneTwo.SmartWaiting.domain.notification.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SSE 전달 요청을 Redis 채널("sse:notification")로 발행한다.
 *
 * <p>SseEmitter는 연결을 받은 인스턴스의 메모리에만 있으므로, 알림을 만든 인스턴스와 손님이 연결된
 * 인스턴스가 다르면 직접 보낼 수 없다. 모든 인스턴스가 이 채널을 구독(SseRedisSubscriber)하고,
 * 해당 회원의 연결을 가진 인스턴스만 실제로 전달한다.
 */
@Component
@RequiredArgsConstructor
public class SseRedisPublisher {

    public static final String CHANNEL = "sse:notification";

    private final StringRedisTemplate redisTemplate;

    // Spring Boot 4.0은 Jackson 2 ObjectMapper 빈이 없어 직접 생성 (RedisChatPublisher와 동일)
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publish(SseRedisMessage message) throws JsonProcessingException {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
    }
}
