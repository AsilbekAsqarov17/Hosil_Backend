package uz.hosil.platform.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable state timeline for leasing application transitions (PRD LEASE-003, LEASE-005).
 */
@Entity
@Table(name = "leasing_application_status_history")
public class LeasingApplicationStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LeasingApplicationStatusHistoryEntity() {
        this.createdAt = Instant.now();
    }

    public LeasingApplicationStatusHistoryEntity(
            UUID applicationId,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorRole,
            String notes
    ) {
        this();
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
