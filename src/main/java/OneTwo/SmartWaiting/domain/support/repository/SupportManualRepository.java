package OneTwo.SmartWaiting.domain.support.repository;

import OneTwo.SmartWaiting.domain.support.entity.SupportManual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportManualRepository extends JpaRepository<SupportManual, Long> {

    Optional<SupportManual> findByIdAndIsDeletedFalse(Long id);

    List<SupportManual> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    List<SupportManual> findAllByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category);
}
