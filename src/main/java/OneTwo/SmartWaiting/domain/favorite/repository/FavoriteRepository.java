package OneTwo.SmartWaiting.domain.favorite.repository;

import OneTwo.SmartWaiting.domain.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByMemberIdAndStoreId(Long memberId, Long storeId);

    boolean deleteByMemberIdAndStoreId(Long memberId, Long storeId);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.store WHERE f.member.id = :memberId ORDER BY f.createdAt DESC")
    List<Favorite> findAllByMemberId(@Param("memberId") Long memberId);
}
