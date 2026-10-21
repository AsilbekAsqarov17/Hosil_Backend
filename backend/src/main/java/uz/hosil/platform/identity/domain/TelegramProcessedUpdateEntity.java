package uz.hosil.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Tracks processed Telegram update IDs to guarantee idempotency and replay prevention.
 */
@Entity
@Table(name = "telegram_processed_updates")
public class TelegramProcessedUpdateEntity {

    @Id
    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected TelegramProcessedUpdateEntity() {
        // JPA constructor
    }

    public TelegramProcessedUpdateEntity(Long updateId) {
        this.updateId = updateId;
        this.processedAt = Instant.now();
    }

    public Long getUpdateId() {
        return updateId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
