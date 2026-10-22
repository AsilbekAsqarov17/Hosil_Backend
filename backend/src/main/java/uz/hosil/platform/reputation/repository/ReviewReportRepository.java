package uz.hosil.platform.reputation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.reputation.domain.ReviewReportEntity;
import uz.hosil.platform.reputation.domain.ReviewReportStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReportEntity, UUID> {

    List<ReviewReportEntity> findByReviewId(UUID reviewId);

    List<ReviewReportEntity> findByStatusOrderByCreatedAtDesc(ReviewReportStatus status);
}
