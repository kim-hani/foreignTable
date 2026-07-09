package OneTwo.SmartWaiting.domain.chat.redis;

import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 채널("chat:room:*")을 구독해, 수신한 채팅 이벤트를
 * 이 인스턴스의 SimpleBroker 구독자들("/sub/chat/room/{roomId}")에게 전달한다.
 *
 * <p>예외 처리 주의: 여기는 Redis 리스너 스레드라 예외를 던져도 요청자에게
 * 전파될 수 없다. BusinessException 대신 로그만 남기고 해당 이벤트를 버린다
 * (프로젝트의 BusinessException 규칙은 요청 처리 흐름에 적용되는 것).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatSubscriber implements MessageListener {

    private static final String BROKER_DESTINATION_PREFIX = "/sub/chat/room/";

    private final SimpMessagingTemplate messagingTemplate;

    // 발행 측(RedisChatPublisher)과 동일하게 Jackson 2 매퍼를 직접 생성 (Boot 4는 Jackson 3만 빈 등록).
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8); // "chat:room:{roomId}"
        try {
            String roomId = channel.substring(channel.lastIndexOf(':') + 1);
            ChatMessageResponseDto dto = objectMapper.readValue(message.getBody(), ChatMessageResponseDto.class);
            messagingTemplate.convertAndSend(BROKER_DESTINATION_PREFIX + roomId, dto);
        } catch (Exception e) {
            log.error("[Chat] Redis 메시지 브로드캐스트 실패 — channel: {}", channel, e);
        }
    }
}
