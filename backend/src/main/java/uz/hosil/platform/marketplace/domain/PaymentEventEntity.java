package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log for incoming payment webhooks, callbacks, and status transitions (PAY-004).
 */
@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "payload_snapshot", columnDefinition = "TEXT")
    private String payloadSnapshot;

    @Column(name = "external_event_id", length = 100)
    private String externalEventId;

    @Column(name = "signature_verified", nullable = false)
    private Boolean signatureVerified = false;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public PaymentEventEntity() {
    }

    public PaymentEventEntity(
            UUID id,
            UUID paymentId,
            String eventType,
            String fromStatus,
            String toStatus,
            String payloadSnapshot,
            String externalEventId,
            Boolean signatureVerified
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.payloadSnapshot = payloadSnapshot;
        this.externalEventId = externalEventId;
        this.signatureVerified = signatureVerified != null ? signatureVerified : false;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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

    public String getPayloadSnapshot() {
        return payloadSnapshot;
    }

    public void setPayloadSnapshot(String payloadSnapshot) {
        this.payloadSnapshot = payloadSnapshot;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public void setExternalEventId(String externalEventId) {
        this.externalEventId = externalEventId;
    }

    public Boolean getSignatureVerified() {
        return signatureVerified;
    }

    public void setSignatureVerified(Boolean signatureVerified) {
        this.signatureVerified = signatureVerified;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
