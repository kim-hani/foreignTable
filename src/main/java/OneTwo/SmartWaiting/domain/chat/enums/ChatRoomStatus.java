package OneTwo.SmartWaiting.domain.chat.enums;

/**
 * 상담 채팅방 상태.
 * WAITING(상담원 배정 대기) → ACTIVE(상담 진행) → CLOSED(종료).
 */
public enum ChatRoomStatus {
    WAITING,
    ACTIVE,
    CLOSED
}
