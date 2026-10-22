package uz.hosil.platform.reputation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable administrative moderation audit log entity per docs/12_reputation_and_reviews.md §5.3 (ADM-005).
 */
@Entity
@Table(name = "review_moderation_logs")
public class ReviewModerationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "justification_code", nullable = false, length = 50)
    private String justificationCode;

    @Column(name = "admin_notes", nullable = false, columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public ReviewModerationLogEntity() {
    }

    public ReviewModerationLogEntity(UUID reviewId, UUID adminUserId, String action, String justificationCode, String adminNotes) {
        this.reviewId = reviewId;
        this.adminUserId = adminUserId;
        this.action = action;
        this.justificationCode = justificationCode;
        this.adminNotes = adminNotes;
        this.recordedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public void setReviewId(UUID reviewId) {
        this.reviewId = reviewId;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getJustificationCode() {
        return justificationCode;
    }

    public void setJustificationCode(String justificationCode) {
        this.justificationCode = justificationCode;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
