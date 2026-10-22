package uz.hosil.platform.farm.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.CropDiseaseDiagnosisEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CropDiseaseDiagnosisEntity operations.
 */
@Repository
public interface CropDiseaseDiagnosisRepository extends JpaRepository<CropDiseaseDiagnosisEntity, UUID> {

    Optional<CropDiseaseDiagnosisEntity> findByIdempotencyKey(String idempotencyKey);

    Page<CropDiseaseDiagnosisEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<CropDiseaseDiagnosisEntity> findByFarmIdOrderByCreatedAtDesc(UUID farmId, Pageable pageable);

    Page<CropDiseaseDiagnosisEntity> findByUserIdAndFarmIdOrderByCreatedAtDesc(UUID userId, UUID farmId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM CropDiseaseDiagnosisEntity d WHERE d.id = :id")
    Optional<CropDiseaseDiagnosisEntity> findByIdForUpdate(@Param("id") UUID id);
}
