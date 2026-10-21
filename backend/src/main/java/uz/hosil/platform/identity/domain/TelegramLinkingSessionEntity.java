package uz.hosil.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Short-lived, single-use linking session representing a pending Telegram authentication deep link.
 * Nonce is stored as a SHA-256 hash to avoid persisting plaintext tokens.
 */
@Entity
@Table(name = "telegram_linking_sessions")
public class TelegramLinkingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "nonce_hash", nullable = false, unique = true, length = 64)
    private String nonceHash;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_consumed", nullable = false)
    private boolean isConsumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TelegramLinkingSessionEntity() {
        // JPA constructor
    }

    public TelegramLinkingSessionEntity(String phoneNumber, String nonceHash, Instant expiresAt) {
        this.phoneNumber = phoneNumber;
        this.nonceHash = nonceHash;
        this.expiresAt = expiresAt;
        this.isConsumed = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getNonceHash() {
        return nonceHash;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isConsumed() {
        return isConsumed;
    }

    public void setConsumed(boolean consumed) {
        isConsumed = consumed;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
