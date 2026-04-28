package OneTwo.SmartWaiting.domain.waiting.repository;

import OneTwo.SmartWaiting.domain.waiting.dto.WaitingStatProjection;
import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaitingRepository extends JpaRepository<Waiting, Long> {
    Long countByStoreIdAndStatus(Long storeId, WaitingStatus status);

    List<Waiting> findAllByStoreIdAndStatusOrderByTicketTimeAsc(Long storeId, WaitingStatus status);

    @Query("SELECT w FROM Waiting w JOIN FETCH w.store WHERE w.member.id = :memberId ORDER BY w.createdAt DESC")
    List<Waiting> findAllByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndStoreIdAndStatus(Long memberId, Long storeId, WaitingStatus status);

    Long countByStoreIdAndStatusAndTicketTimeLessThan(Long storeId, WaitingStatus status, LocalDateTime createdAt);

    Page<Waiting> findByStoreIdAndStatusOrderByTicketTimeAsc(Long storeId, WaitingStatus status, Pageable pageable);

    Long countByStoreIdAndStatusIn(Long storeId, List<WaitingStatus> statuses);

    Long countByStoreIdAndStatusInAndTicketTimeLessThan(Long storeId, List<WaitingStatus> statuses, LocalDateTime ticketTime);

    Page<Waiting> findByStoreIdAndStatusInOrderByTicketTimeAsc(Long storeId, List<WaitingStatus> statuses, Pageable pageable);

    List<Waiting> findAllByStatusAndUpdatedAtBetween(WaitingStatus status, LocalDateTime start, LocalDateTime end);

    List<Waiting> findAllByStatusIn(List<WaitingStatus> list);

    // 최근 하루 동안 웨이팅이 발생한 '활성화된' 식당 ID만 조회
    @Query("SELECT DISTINCT w.store.id FROM Waiting w WHERE w.ticketTime >= :yesterday")
    List<Long> findStoreIdsWithWaitingsSince(@Param("yesterday") LocalDateTime yesterday);

    // 자바 대신 DB가 직접 요일별/시간별 평균을 계산해서 반환!
    // ISODOW: 1(월요일) ~ 7(일요일) 반환
    @Query(value = "SELECT " +
            "  CAST(EXTRACT(ISODOW FROM ticket_time) AS INTEGER) AS dayOfWeek, " +
            "  CAST(EXTRACT(HOUR FROM ticket_time) AS INTEGER) AS hourOfDay, " +
            "  COUNT(*) / 4.0 AS avgTeams, " +
            "  AVG(expected_wait_min) AS avgWaitMin " +
            "FROM waiting " +
            "WHERE store_id = :storeId AND ticket_time >= :oneMonthAgo " +
            "GROUP BY EXTRACT(ISODOW FROM ticket_time), EXTRACT(HOUR FROM ticket_time)",
            nativeQuery = true)
    List<WaitingStatProjection> findWaitingStatsByStoreId(@Param("storeId") Long storeId, @Param("oneMonthAgo") LocalDateTime oneMonthAgo);
}
