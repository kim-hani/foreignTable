package OneTwo.SmartWaiting.domain.chat.repository;

import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 사용자의 열린 방(WAITING/ACTIVE) 조회 — 방 생성 API의 멱등 재사용에 사용. */
    Optional<ChatRoom> findFirstByMemberIdAndStatusNotAndIsDeletedFalse(Long memberId, ChatRoomStatus status);

    /** 상담원 콘솔용 상태별 방 목록 (오래된 요청부터 — 선착순 응대). */
    Slice<ChatRoom> findAllByStatusAndIsDeletedFalseOrderByCreatedAtAsc(ChatRoomStatus status, Pageable pageable);

    /**
     * 상담원 배정 — WAITING 상태일 때만 원자적으로 담당자를 지정한다(동시 수락 경합 방지).
     * 반환값이 0이면 이미 다른 상담원이 배정됐거나 종료된 방이다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatRoom c SET c.admin = :admin, c.status = 'ACTIVE' " +
            "WHERE c.id = :roomId AND c.status = 'WAITING' AND c.isDeleted = false")
    int claim(@Param("roomId") Long roomId, @Param("admin") Member admin);
}
