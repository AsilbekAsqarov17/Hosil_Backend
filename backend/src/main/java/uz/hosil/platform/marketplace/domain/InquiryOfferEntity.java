package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable negotiation offer / counteroffer within an RFQ inquiry.
 */
@Entity
@Table(name = "inquiry_offers")
public class InquiryOfferEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "offer_number", nullable = false)
    private Integer offerNumber;

    @Column(name = "proposer_user_id", nullable = false)
    private UUID proposerUserId;

    @Column(name = "proposer_role", nullable = false, length = 20)
    private String proposerRole; // BUYER, SELLER

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @Column(name = "total_price", precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "delivery_terms", length = 255)
    private String deliveryTerms;

    @Column(name = "notes")
    private String notes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PROPOSED, ACCEPTED, SUPERSEDED, REJECTED, EXPIRED

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InquiryOfferEntity() {}

    public InquiryOfferEntity(
            UUID id,
            UUID inquiryId,
            Integer offerNumber,
            UUID proposerUserId,
            String proposerRole,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            String currency,
            String deliveryTerms,
            String notes,
            Instant expiresAt,
            String idempotencyKey
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.inquiryId = inquiryId;
        this.offerNumber = offerNumber;
        this.proposerUserId = proposerUserId;
        this.proposerRole = proposerRole;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.unitPrice = unitPrice;
        this.currency = currency != null ? currency : "UZS";
        this.totalPrice = (unitPrice != null && quantity != null) ? unitPrice.multiply(quantity) : null;
        this.deliveryTerms = deliveryTerms;
        this.notes = notes;
        this.expiresAt = expiresAt;
        this.status = "PROPOSED";
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getInquiryId() { return inquiryId; }
    public void setInquiryId(UUID inquiryId) { this.inquiryId = inquiryId; }

    public Integer getOfferNumber() { return offerNumber; }
    public void setOfferNumber(Integer offerNumber) { this.offerNumber = offerNumber; }

    public UUID getProposerUserId() { return proposerUserId; }
    public void setProposerUserId(UUID proposerUserId) { this.proposerUserId = proposerUserId; }

    public String getProposerRole() { return proposerRole; }
    public void setProposerRole(String proposerRole) { this.proposerRole = proposerRole; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.totalPrice = (unitPrice != null && this.quantity != null) ? unitPrice.multiply(this.quantity) : null;
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getDeliveryTerms() { return deliveryTerms; }
    public void setDeliveryTerms(String deliveryTerms) { this.deliveryTerms = deliveryTerms; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
