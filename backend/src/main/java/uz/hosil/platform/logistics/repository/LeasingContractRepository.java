package uz.hosil.platform.logistics.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.logistics.domain.LeasingContractEntity;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeasingContractRepository extends JpaRepository<LeasingContractEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM LeasingContractEntity c WHERE c.id = :id")
    Optional<LeasingContractEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<LeasingContractEntity> findByContractNumber(String contractNumber);

    Optional<LeasingContractEntity> findByApplicationId(UUID applicationId);

    Page<LeasingContractEntity> findByLesseeUserId(UUID lesseeUserId, Pageable pageable);

    Page<LeasingContractEntity> findByLessorUserId(UUID lessorUserId, Pageable pageable);

    Optional<LeasingContractEntity> findByEquipmentIdAndStatusIn(UUID equipmentId, Collection<String> statuses);

    boolean existsByEquipmentIdAndStatusIn(UUID equipmentId, Collection<String> statuses);
}
