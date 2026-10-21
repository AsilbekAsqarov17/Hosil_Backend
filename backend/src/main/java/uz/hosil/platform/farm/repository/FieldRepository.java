package uz.hosil.platform.farm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.FieldEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FieldRepository extends JpaRepository<FieldEntity, UUID> {

    Page<FieldEntity> findByFarmId(UUID farmId, Pageable pageable);

    Optional<FieldEntity> findByIdAndFarmId(UUID id, UUID farmId);

    boolean existsByIdAndFarmId(UUID id, UUID farmId);
}
