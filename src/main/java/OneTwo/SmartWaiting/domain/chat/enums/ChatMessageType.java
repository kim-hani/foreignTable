package OneTwo.SmartWaiting.domain.chat.enums;

/**
 * WebSocket으로 브로드캐스트되는 채팅 이벤트 종류.
 *
 * <ul>
 *   <li>TALK — 참여자가 보낸 일반 메시지 (DB 저장)</li>
 *   <li>SYSTEM — 상담원 배정/종료 등 상태 전환 안내 (브로드캐스트 전용, DB 미저장)</li>
 *   <li>READ — 읽음 영수증: 상대방 화면의 "읽음" 표시 갱신용 (브로드캐스트 전용, DB 미저장)</li>
 * </ul>
 */
public enum ChatMessageType {
    TALK,
    SYSTEM,
    READ
}
