package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.TelegramUserLinkEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramUserLinkRepository extends JpaRepository<TelegramUserLinkEntity, UUID> {
    Optional<TelegramUserLinkEntity> findByPhoneNumber(String phoneNumber);
    Optional<TelegramUserLinkEntity> findByTelegramChatId(Long telegramChatId);
    Optional<TelegramUserLinkEntity> findByTelegramUserId(Long telegramUserId);
    boolean existsByPhoneNumber(String phoneNumber);
    void deleteByPhoneNumber(String phoneNumber);
}
