package uz.hosil.platform.reputation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.reputation.domain.ReviewModerationLogEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewModerationLogRepository extends JpaRepository<ReviewModerationLogEntity, UUID> {

    List<ReviewModerationLogEntity> findByReviewIdOrderByRecordedAtDesc(UUID reviewId);
}
