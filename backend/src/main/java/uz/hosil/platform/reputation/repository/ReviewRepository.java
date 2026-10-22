package uz.hosil.platform.reputation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.reputation.domain.ReviewEntity;
import uz.hosil.platform.reputation.domain.ReviewStatus;
import uz.hosil.platform.reputation.domain.ReviewType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    Optional<ReviewEntity> findByOrderIdAndAuthorIdAndReviewType(UUID orderId, UUID authorId, ReviewType reviewType);

    Optional<ReviewEntity> findByOrderIdAndOrderItemIdAndAuthorId(UUID orderId, UUID orderItemId, UUID authorId);

    List<ReviewEntity> findByOrderId(UUID orderId);

    List<ReviewEntity> findByTargetUserIdAndStatusOrderByPublishedAtDesc(UUID targetUserId, ReviewStatus status);

    List<ReviewEntity> findByTargetUserIdAndReviewTypeAndStatus(UUID targetUserId, ReviewType reviewType, ReviewStatus status);

    List<ReviewEntity> findByTargetListingIdAndStatusOrderByPublishedAtDesc(UUID targetListingId, ReviewStatus status);

    List<ReviewEntity> findByStatusAndBlindUntilLessThanEqual(ReviewStatus status, Instant cutoff);
}
