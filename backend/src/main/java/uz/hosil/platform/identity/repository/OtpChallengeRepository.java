package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.OtpChallengeEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallengeEntity, UUID> {
    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant since);
    List<OtpChallengeEntity> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
