package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.identity.domain.CarrierProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface CarrierProfileRepository extends JpaRepository<CarrierProfileEntity, UUID> {

    Optional<CarrierProfileEntity> findByUserId(UUID userId);

    boolean existsByUserIdAndVerificationStatus(UUID userId, String verificationStatus);
}
