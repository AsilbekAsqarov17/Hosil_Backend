package uz.hosil.platform.logistics.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.logistics.domain.ShipmentRequestEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequestEntity, UUID>, JpaSpecificationExecutor<ShipmentRequestEntity> {

    Optional<ShipmentRequestEntity> findByRequesterUserIdAndIdempotencyKey(UUID requesterUserId, String idempotencyKey);

    Optional<ShipmentRequestEntity> findByRequestNumber(String requestNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShipmentRequestEntity s WHERE s.id = :id")
    Optional<ShipmentRequestEntity> findByIdForUpdate(@Param("id") UUID id);

    List<ShipmentRequestEntity> findByOrderIdAndStatusIn(UUID orderId, Collection<String> statuses);

    List<ShipmentRequestEntity> findByHarvestBatchIdAndStatusIn(UUID harvestBatchId, Collection<String> statuses);

    List<ShipmentRequestEntity> findByHarvestBatchIdAndOrderIdIsNullAndStatusIn(UUID harvestBatchId, Collection<String> statuses);

    Page<ShipmentRequestEntity> findByStatusIn(Collection<String> statuses, Pageable pageable);

    Page<ShipmentRequestEntity> findByRequesterUserId(UUID requesterUserId, Pageable pageable);

    Page<ShipmentRequestEntity> findByAssignedCarrierUserId(UUID assignedCarrierUserId, Pageable pageable);
}
