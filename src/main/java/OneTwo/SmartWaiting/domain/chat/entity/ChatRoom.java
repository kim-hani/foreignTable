package OneTwo.SmartWaiting.domain.chat.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * USER ↔ ADMIN 1:1 상담 채팅방.
 *
 * <p>AI 챗봇이 답하지 못한(needsAgent=true) 문의를 사람이 이어받는 채널.
 * 생성 시 WAITING으로 시작하고, 상담원이 수락(claim)하면 ACTIVE, 상담이 끝나면 CLOSED가 된다.
 * 상담원 배정(claim)은 동시 수락 경합을 막기 위해 엔티티 메서드가 아닌
 * 조건부 UPDATE 쿼리(ChatRoomRepository.claim)로만 수행한다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoom extends BaseEntity {

    /** 상담을 요청한 일반 사용자. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 담당 상담원(ADMIN). WAITING 동안에는 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Member admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRoomStatus status;

    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }

    /** 방 주인(USER) 또는 담당 상담원인지 확인한다. */
    public boolean isParticipant(Long memberId) {
        if (member.getId().equals(memberId)) {
            return true;
        }
        return admin != null && admin.getId().equals(memberId);
    }
}
