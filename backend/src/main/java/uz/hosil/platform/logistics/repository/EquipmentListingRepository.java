package uz.hosil.platform.logistics.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.logistics.domain.EquipmentListingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentListingRepository extends JpaRepository<EquipmentListingEntity, UUID>, JpaSpecificationExecutor<EquipmentListingEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EquipmentListingEntity e WHERE e.id = :id")
    Optional<EquipmentListingEntity> findByIdForUpdate(@Param("id") UUID id);

    Page<EquipmentListingEntity> findByAvailabilityStatusAndPublicationStatus(
            String availabilityStatus,
            String publicationStatus,
            Pageable pageable
    );

    Page<EquipmentListingEntity> findByCategoryAndAvailabilityStatusAndPublicationStatus(
            String category,
            String availabilityStatus,
            String publicationStatus,
            Pageable pageable
    );

    Page<EquipmentListingEntity> findByOwnerUserId(UUID ownerUserId, Pageable pageable);
}
