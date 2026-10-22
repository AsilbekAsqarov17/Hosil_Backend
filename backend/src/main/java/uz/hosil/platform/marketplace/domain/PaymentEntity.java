package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Provider-neutral payment attempt/transaction entity (PAY-001, PAY-002, DEC-007).
 * Strictly decoupled from OrderEntity.status.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "payment_intent_reference", nullable = false, unique = true, length = 100)
    private String paymentIntentReference;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PaymentEntity() {
    }

    public PaymentEntity(
            UUID id,
            UUID orderId,
            UUID buyerUserId,
            String paymentIntentReference,
            String providerName,
            BigDecimal amount,
            String currency,
            String status,
            String idempotencyKey,
            Instant expiresAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.buyerUserId = buyerUserId;
        this.paymentIntentReference = paymentIntentReference;
        this.providerName = providerName;
        this.amount = amount;
        this.currency = currency != null ? currency : "UZS";
        this.status = status != null ? status : "INITIATED";
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.version = 0L;
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

    public UUID getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(UUID buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public String getPaymentIntentReference() {
        return paymentIntentReference;
    }

    public void setPaymentIntentReference(String paymentIntentReference) {
        this.paymentIntentReference = paymentIntentReference;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
