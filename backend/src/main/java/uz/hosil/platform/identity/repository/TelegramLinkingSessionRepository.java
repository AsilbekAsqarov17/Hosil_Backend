package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.TelegramLinkingSessionEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramLinkingSessionRepository extends JpaRepository<TelegramLinkingSessionEntity, UUID> {
    Optional<TelegramLinkingSessionEntity> findByNonceHash(String nonceHash);
    Optional<TelegramLinkingSessionEntity> findFirstByTelegramUserIdAndIsConsumedFalseOrderByCreatedAtDesc(Long telegramUserId);
    Optional<TelegramLinkingSessionEntity> findFirstByPhoneNumberAndIsConsumedFalseOrderByCreatedAtDesc(String phoneNumber);
    List<TelegramLinkingSessionEntity> findByPhoneNumberAndIsConsumedFalse(String phoneNumber);
    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant after);
}
