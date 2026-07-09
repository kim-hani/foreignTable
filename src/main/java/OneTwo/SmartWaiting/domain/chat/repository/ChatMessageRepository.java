package OneTwo.SmartWaiting.domain.chat.repository;

import OneTwo.SmartWaiting.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 메시지 이력 (최신순 페이지네이션). */
    Slice<ChatMessage> findAllByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    /** 조회 주체 기준 미읽음 개수 (상대방이 보낸 메시지 중 isRead=false). */
    long countByChatRoomIdAndSenderIdNotAndIsReadFalse(Long chatRoomId, Long readerId);

    /**
     * 방 단위 벌크 읽음 처리 — 상대방이 보낸 미읽음 메시지를 한 번에 읽음으로 표시한다.
     * 반환값은 처리 건수(0이면 읽을 것이 없었으므로 READ 이벤트 발행 생략).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.isRead = true " +
            "WHERE m.chatRoom.id = :roomId AND m.sender.id <> :readerId AND m.isRead = false")
    int markAllAsRead(@Param("roomId") Long roomId, @Param("readerId") Long readerId);
}
