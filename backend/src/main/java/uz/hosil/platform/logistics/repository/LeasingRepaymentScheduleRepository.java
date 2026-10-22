package uz.hosil.platform.logistics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.logistics.domain.LeasingRepaymentScheduleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeasingRepaymentScheduleRepository extends JpaRepository<LeasingRepaymentScheduleEntity, UUID> {

    List<LeasingRepaymentScheduleEntity> findByContractIdOrderByInstallmentNumberAsc(UUID contractId);

    Optional<LeasingRepaymentScheduleEntity> findByContractIdAndInstallmentNumber(UUID contractId, Integer installmentNumber);
}
