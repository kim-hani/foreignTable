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

    // ===== 영업 분석 대시보드(#10) 집계 쿼리 =====

    // 기간 내 상태별 등록 건수 (총 건수 + 노쇼율 계산에 사용)
    @Query("SELECT w.status, COUNT(w) FROM Waiting w " +
            "WHERE w.store.id = :storeId AND w.createdAt >= :since GROUP BY w.status")
    List<Object[]> findStatusCountsSince(@Param("storeId") Long storeId, @Param("since") LocalDateTime since);

    // 기간 내 SEATED 평균 대기 시간(분): 발권(ticket_time) → 착석(updated_at)
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (updated_at - ticket_time)) / 60) " +
            "FROM waiting " +
            "WHERE store_id = :storeId AND status = 'SEATED' AND created_at >= :since",
            nativeQuery = true)
    Double findAverageWaitMinutesSince(@Param("storeId") Long storeId, @Param("since") LocalDateTime since);

    // 기간 내 혼잡 TOP 3 시간대 (등록 건수 내림차순)
    @Query(value = "SELECT CAST(EXTRACT(HOUR FROM created_at) AS INTEGER) AS hour, COUNT(*) AS cnt " +
            "FROM waiting " +
            "WHERE store_id = :storeId AND created_at >= :since " +
            "GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY cnt DESC LIMIT 3",
            nativeQuery = true)
    List<Object[]> findTop3BusyHoursSince(@Param("storeId") Long storeId, @Param("since") LocalDateTime since);

    // 기간 내 인원수별 등록 건수 분포
    @Query("SELECT w.headCount, COUNT(w) FROM Waiting w " +
            "WHERE w.store.id = :storeId AND w.createdAt >= :since " +
            "GROUP BY w.headCount ORDER BY w.headCount")
    List<Object[]> findHeadCountDistributionSince(@Param("storeId") Long storeId, @Param("since") LocalDateTime since);
}
