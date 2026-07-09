package OneTwo.SmartWaiting.domain.chat.redis;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 이벤트를 Redis 채널("chat:room:{roomId}")로 발행한다.
 *
 * <p>WebSocket 브로커(SimpleBroker)는 인스턴스별 인메모리라, 발신자와 수신자가
 * 다른 인스턴스에 붙어 있으면 직접 전달이 불가능하다. 모든 인스턴스가 같은
 * Redis 채널을 구독(RedisChatSubscriber)하므로 이 발행 한 번으로 전 인스턴스에 전파된다.
 */
@Component
@RequiredArgsConstructor
public class RedisChatPublisher {

    public static final String CHANNEL_PREFIX = "chat:room:";

    private final StringRedisTemplate redisTemplate;

    // Spring Boot 4.0은 Jackson 3(tools.jackson)를 자동 구성해 Jackson 2 ObjectMapper 빈이 없다.
    // 프로젝트의 JwtAuthenticationEntryPoint와 동일하게 Jackson 2 매퍼를 직접 생성해 쓴다.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public void publish(Long roomId, ChatMessageResponseDto message) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + roomId, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_SEND_FAILED);
        }
    }
}
