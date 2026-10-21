package uz.hosil.platform.farm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmMembershipRepository extends JpaRepository<FarmMembershipEntity, UUID> {

    Optional<FarmMembershipEntity> findByFarmIdAndUserId(UUID farmId, UUID userId);

    Optional<FarmMembershipEntity> findByFarmIdAndUserIdAndStatus(UUID farmId, UUID userId, String status);

    List<FarmMembershipEntity> findByFarmId(UUID farmId);

    List<FarmMembershipEntity> findByFarmIdAndStatus(UUID farmId, String status);

    List<FarmMembershipEntity> findByUserIdAndStatus(UUID userId, String status);

    boolean existsByFarmIdAndUserIdAndStatus(UUID farmId, UUID userId, String status);
}
