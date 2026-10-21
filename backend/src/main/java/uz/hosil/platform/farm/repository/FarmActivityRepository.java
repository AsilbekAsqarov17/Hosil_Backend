package uz.hosil.platform.farm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.FarmActivityEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmActivityRepository extends JpaRepository<FarmActivityEntity, UUID> {

    Page<FarmActivityEntity> findByFarmId(UUID farmId, Pageable pageable);

    Page<FarmActivityEntity> findByFarmIdAndCropCycleId(UUID farmId, UUID cropCycleId, Pageable pageable);

    Page<FarmActivityEntity> findByFarmIdAndFieldId(UUID farmId, UUID fieldId, Pageable pageable);

    Page<FarmActivityEntity> findByFarmIdAndFieldIdAndCropCycleId(UUID farmId, UUID fieldId, UUID cropCycleId, Pageable pageable);

    Optional<FarmActivityEntity> findByFarmIdAndClientMutationId(UUID farmId, String clientMutationId);

    Optional<FarmActivityEntity> findByIdAndFarmId(UUID id, UUID farmId);
}
