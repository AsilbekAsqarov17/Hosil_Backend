package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.identity.domain.SellerProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface SellerProfileRepository extends JpaRepository<SellerProfileEntity, UUID> {

    Optional<SellerProfileEntity> findByUserId(UUID userId);
}
