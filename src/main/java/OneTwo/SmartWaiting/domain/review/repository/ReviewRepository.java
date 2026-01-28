package OneTwo.SmartWaiting.domain.review.repository;

import OneTwo.SmartWaiting.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long >{

    List<Review> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);
}
