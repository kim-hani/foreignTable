package OneTwo.SmartWaiting.domain.chat.dto;

import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatRoomResponseDto(
        Long roomId,
        Long memberId,
        String memberName,
        Long adminId,
        String adminName,
        ChatRoomStatus status,
        long unreadCount,
        LocalDateTime createdAt
) {

    /** unreadCount는 조회 주체 기준(상대방이 보낸 미읽음 수)으로 서비스에서 계산해 넘긴다. */
    public static ChatRoomResponseDto from(ChatRoom room, long unreadCount) {
        return ChatRoomResponseDto.builder()
                .roomId(room.getId())
                .memberId(room.getMember().getId())
                .memberName(room.getMember().getNickname())
                .adminId(room.getAdmin() == null ? null : room.getAdmin().getId())
                .adminName(room.getAdmin() == null ? null : room.getAdmin().getNickname())
                .status(room.getStatus())
                .unreadCount(unreadCount)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
