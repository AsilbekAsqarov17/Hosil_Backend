package uz.hosil.platform.farm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.CropCycleEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CropCycleRepository extends JpaRepository<CropCycleEntity, UUID> {

    Page<CropCycleEntity> findByFarmId(UUID farmId, Pageable pageable);

    Page<CropCycleEntity> findByFarmIdAndFieldId(UUID farmId, UUID fieldId, Pageable pageable);

    Page<CropCycleEntity> findByFarmIdAndStatus(UUID farmId, String status, Pageable pageable);

    Page<CropCycleEntity> findByFarmIdAndFieldIdAndStatus(UUID farmId, UUID fieldId, String status, Pageable pageable);

    Optional<CropCycleEntity> findByIdAndFarmId(UUID id, UUID farmId);

    long countByFarmIdAndStatus(UUID farmId, String status);
}
