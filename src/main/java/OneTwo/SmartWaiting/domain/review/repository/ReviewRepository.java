package OneTwo.SmartWaiting.domain.review.repository;

import OneTwo.SmartWaiting.domain.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long >{

    Slice<Review> findAllByStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    boolean existsByWaitingId(Long waitingId);

    Slice<Review> findAllByMemberIdOrderByCreatedAtDesc(Long memverId,Pageable pageable);

    List<Review> findTop50ByStoreIdOrderByCreatedAtDesc(Long storeId);
}
