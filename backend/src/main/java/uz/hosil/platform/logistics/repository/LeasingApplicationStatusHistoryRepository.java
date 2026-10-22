package uz.hosil.platform.logistics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.logistics.domain.LeasingApplicationStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeasingApplicationStatusHistoryRepository extends JpaRepository<LeasingApplicationStatusHistoryEntity, UUID> {

    List<LeasingApplicationStatusHistoryEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
