package uz.hosil.platform.marketplace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HarvestListingRepository extends JpaRepository<HarvestListingEntity, UUID>, JpaSpecificationExecutor<HarvestListingEntity> {

    Optional<HarvestListingEntity> findBySellerUserIdAndIdempotencyKey(UUID sellerUserId, String idempotencyKey);

    List<HarvestListingEntity> findByHarvestBatchId(UUID harvestBatchId);

    Optional<HarvestListingEntity> findByIdAndStatus(UUID id, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM HarvestListingEntity l WHERE l.id = :id")
    Optional<HarvestListingEntity> findByIdForUpdate(@Param("id") UUID id);
}
