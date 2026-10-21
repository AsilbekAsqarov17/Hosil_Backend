package uz.hosil.platform.marketplace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {

    Optional<InventoryReservationEntity> findByOrderId(UUID orderId);

    List<InventoryReservationEntity> findAllByOrderId(UUID orderId);

    Optional<InventoryReservationEntity> findByOrderIdAndStatus(UUID orderId, String status);

    List<InventoryReservationEntity> findAllByOrderIdAndStatus(UUID orderId, String status);

    List<InventoryReservationEntity> findByHarvestListingId(UUID harvestListingId);

    List<InventoryReservationEntity> findBySellerOfferId(UUID sellerOfferId);

    @Query("""
        SELECT r.id FROM InventoryReservationEntity r
        WHERE r.status = 'ACTIVE' AND r.expiresAt <= :now
        ORDER BY r.expiresAt ASC
    """)
    List<UUID> findExpiredActiveReservationIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservationEntity r WHERE r.id = :id")
    Optional<InventoryReservationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE InventoryReservationEntity r
        SET r.status = :newStatus
        WHERE r.id = :id AND r.status = 'ACTIVE'
    """)
    int transitionStatusIfActive(@Param("id") UUID id, @Param("newStatus") String newStatus);
}
