package OneTwo.SmartWaiting.domain.chat.dto;

import OneTwo.SmartWaiting.domain.chat.entity.ChatMessage;
import OneTwo.SmartWaiting.domain.chat.enums.ChatMessageType;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 채팅 이벤트 공용 페이로드 — WebSocket 브로드캐스트, REST 이력 조회, Redis 직렬화(JSON)에 함께 쓰인다.
 *
 * <p>type에 따라 의미가 달라진다:
 * TALK는 저장된 메시지, SYSTEM은 상태 전환 안내(sender 없음),
 * READ는 읽음 영수증(senderId=읽은 사람, content 없음).
 */
@Builder
public record ChatMessageResponseDto(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderName,
        ChatMessageType type,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {

    public static ChatMessageResponseDto from(ChatMessage message) {
        return ChatMessageResponseDto.builder()
                .messageId(message.getId())
                .roomId(message.getChatRoom().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getNickname())
                .type(ChatMessageType.TALK)
                .content(message.getContent())
                .isRead(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public static ChatMessageResponseDto system(Long roomId, String content) {
        return ChatMessageResponseDto.builder()
                .roomId(roomId)
                .type(ChatMessageType.SYSTEM)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static ChatMessageResponseDto read(Long roomId, Long readerId) {
        return ChatMessageResponseDto.builder()
                .roomId(roomId)
                .senderId(readerId)
                .type(ChatMessageType.READ)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
