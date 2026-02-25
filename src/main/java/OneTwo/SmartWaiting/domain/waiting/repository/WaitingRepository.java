package OneTwo.SmartWaiting.domain.waiting.repository;

import OneTwo.SmartWaiting.domain.waiting.entity.Waiting;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaitingRepository extends JpaRepository<Waiting, Long> {
    Long countByStoreIdAndStatus(Long storeId, WaitingStatus status);

    List<Waiting> findAllByStoreIdAndStatus(Long storeId, WaitingStatus status);

    @Query("SELECT w FROM Waiting w JOIN FETCH w.store WHERE w.member.id = :memberId ORDER BY w.createdAt DESC")
    List<Waiting> findAllByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndStoreIdAndStatus(Long memberId, Long storeId, WaitingStatus status);

    Long countByStoreIdAndStatusAndCreatedAtLessThan(Long storeId, WaitingStatus status, LocalDateTime createdAt);
}
