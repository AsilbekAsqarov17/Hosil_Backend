package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.UserRoleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
    List<UserRoleEntity> findByUserId(UUID userId);
    boolean existsByUserIdAndRoleName(UUID userId, String roleName);
}
