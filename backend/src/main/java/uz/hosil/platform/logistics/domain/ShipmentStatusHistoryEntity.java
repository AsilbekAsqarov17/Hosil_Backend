package uz.hosil.platform.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable auditable status log for shipment requests (Table 30, LOG-006, LOG-009).
 */
@Entity
@Table(name = "shipment_status_history")
public class ShipmentStatusHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "shipment_request_id", nullable = false)
    private UUID shipmentRequestId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ShipmentStatusHistoryEntity() {}

    public ShipmentStatusHistoryEntity(
            UUID id,
            UUID shipmentRequestId,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorRole,
            String notes
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.shipmentRequestId = shipmentRequestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getShipmentRequestId() { return shipmentRequestId; }
    public void setShipmentRequestId(UUID shipmentRequestId) { this.shipmentRequestId = shipmentRequestId; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
}
