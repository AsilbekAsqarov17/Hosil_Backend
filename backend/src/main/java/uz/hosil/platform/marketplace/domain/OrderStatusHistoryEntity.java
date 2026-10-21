package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail recording every state transition of an order (Table 23).
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "from_status", length = 35)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 35)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(name = "reason")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderStatusHistoryEntity() {}

    public OrderStatusHistoryEntity(
            UUID id,
            UUID orderId,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorRole,
            String reason
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole != null ? actorRole : "SYSTEM";
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
