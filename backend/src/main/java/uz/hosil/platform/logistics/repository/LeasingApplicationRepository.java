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
import uz.hosil.platform.logistics.domain.LeasingApplicationEntity;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeasingApplicationRepository extends JpaRepository<LeasingApplicationEntity, UUID>, JpaSpecificationExecutor<LeasingApplicationEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LeasingApplicationEntity a WHERE a.id = :id")
    Optional<LeasingApplicationEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<LeasingApplicationEntity> findByApplicationNumber(String applicationNumber);

    Optional<LeasingApplicationEntity> findByApplicantUserIdAndIdempotencyKey(UUID applicantUserId, String idempotencyKey);

    Page<LeasingApplicationEntity> findByApplicantUserId(UUID applicantUserId, Pageable pageable);

    Page<LeasingApplicationEntity> findByApplicantUserIdAndStatus(UUID applicantUserId, String status, Pageable pageable);

    Page<LeasingApplicationEntity> findByEquipmentId(UUID equipmentId, Pageable pageable);

    boolean existsByApplicantUserIdAndEquipmentIdAndStatusIn(
            UUID applicantUserId,
            UUID equipmentId,
            Collection<String> statuses
    );

    boolean existsByEquipmentIdAndStatusIn(
            UUID equipmentId,
            Collection<String> statuses
    );
}
