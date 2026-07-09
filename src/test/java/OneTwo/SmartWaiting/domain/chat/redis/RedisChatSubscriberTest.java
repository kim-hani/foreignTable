package OneTwo.SmartWaiting.domain.chat.redis;

import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.enums.ChatMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisChatSubscriberTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RedisChatSubscriber redisChatSubscriber;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        redisChatSubscriber = new RedisChatSubscriber(messagingTemplate);
    }

    @Test
    @DisplayName("Redis 수신 성공 - 채널의 roomId로 브로커 목적지를 만들어 그대로 브로드캐스트한다.")
    void onMessage_ValidJson_SendsToBrokerDestination() throws Exception {
        // given
        ChatMessageResponseDto dto = ChatMessageResponseDto.builder()
                .messageId(100L)
                .roomId(7L)
                .senderId(1L)
                .senderName("고객")
                .type(ChatMessageType.TALK)
                .content("환불 문의드립니다.")
                .createdAt(LocalDateTime.now())
                .build();
        byte[] body = objectMapper.writeValueAsBytes(dto);
        DefaultMessage message = new DefaultMessage("chat:room:7".getBytes(StandardCharsets.UTF_8), body);

        // when
        redisChatSubscriber.onMessage(message, null);

        // then
        ArgumentCaptor<ChatMessageResponseDto> captor = ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/room/7"), captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("환불 문의드립니다.");
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.TALK);
    }

    @Test
    @DisplayName("Redis 수신 - 역직렬화에 실패해도 예외를 던지지 않고 이벤트를 버린다(리스너 스레드 보호).")
    void onMessage_InvalidJson_DropsEventWithoutThrowing() {
        // given
        DefaultMessage message = new DefaultMessage(
                "chat:room:7".getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8));

        // when
        redisChatSubscriber.onMessage(message, null);

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) org.mockito.ArgumentMatchers.any());
    }
}
