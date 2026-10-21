package uz.hosil.platform.farm.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;

import java.util.Optional;
import java.util.UUID;

public interface HarvestBatchRepository extends JpaRepository<HarvestBatchEntity, UUID> {

    Optional<HarvestBatchEntity> findByIdAndFarmId(UUID id, UUID farmId);

    Page<HarvestBatchEntity> findByFarmId(UUID farmId, Pageable pageable);

    Optional<HarvestBatchEntity> findByFarmIdAndClientBatchId(UUID farmId, String clientBatchId);

    Optional<HarvestBatchEntity> findByFarmIdAndIdempotencyKey(UUID farmId, String idempotencyKey);

    /**
     * Acquires row-level pessimistic write lock (SELECT ... FOR UPDATE) to ensure
     * safe concurrent stock allocation and quantity conservation across transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM HarvestBatchEntity b WHERE b.id = :id")
    Optional<HarvestBatchEntity> findByIdForUpdate(@Param("id") UUID id);
}
