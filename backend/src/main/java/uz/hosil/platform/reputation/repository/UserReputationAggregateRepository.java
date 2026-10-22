package uz.hosil.platform.reputation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.reputation.domain.UserReputationAggregateEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserReputationAggregateRepository extends JpaRepository<UserReputationAggregateEntity, UUID> {

    Optional<UserReputationAggregateEntity> findByUserIdAndRoleContext(UUID userId, String roleContext);
}
