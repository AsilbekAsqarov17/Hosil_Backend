package uz.hosil.platform.reputation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.reputation.domain.ReviewResponseEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewResponseRepository extends JpaRepository<ReviewResponseEntity, UUID> {

    Optional<ReviewResponseEntity> findByReviewId(UUID reviewId);

    List<ReviewResponseEntity> findByReviewIdIn(Collection<UUID> reviewIds);
}
