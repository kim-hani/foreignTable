package OneTwo.SmartWaiting.domain.chat.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상담 채팅 메시지 (TALK 전용 — SYSTEM/READ 이벤트는 브로드캐스트만 하고 저장하지 않는다).
 *
 * <p>읽음 처리는 1:1 채팅 특성상 메시지 단건이 아닌 방 단위 벌크 UPDATE로 수행한다
 * (ChatMessageRepository.markAllAsRead — "상대방이 보낸 미읽음 전부"를 한 번에).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 상대방이 이 메시지를 읽었는지 여부. */
    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;
}
