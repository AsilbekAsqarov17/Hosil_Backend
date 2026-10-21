package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.AuthSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
    Optional<AuthSessionEntity> findByTokenHash(String tokenHash);
    List<AuthSessionEntity> findByUserIdAndIsRevokedFalse(UUID userId);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.isRevoked = true WHERE s.userId = :userId AND s.isRevoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);
}
