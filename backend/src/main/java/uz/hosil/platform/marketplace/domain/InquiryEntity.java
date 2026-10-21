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
 * Represents a pre-order Request-for-Quote (RFQ) inquiry between a buyer and harvest seller (DEC-006).
 */
@Entity
@Table(name = "inquiries")
public class InquiryEntity {

    @Id
    private UUID id;

    @Column(name = "harvest_listing_id", nullable = false)
    private UUID harvestListingId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "crop_type", nullable = false, length = 100)
    private String cropType;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // OPEN, AGREED, REJECTED, WITHDRAWN, EXPIRED

    @Column(name = "current_offer_id")
    private UUID currentOfferId;

    @Column(name = "agreed_offer_id")
    private UUID agreedOfferId;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @Column(name = "agreed_quantity", precision = 14, scale = 3)
    private BigDecimal agreedQuantity;

    @Column(name = "agreed_unit_price", precision = 14, scale = 2)
    private BigDecimal agreedUnitPrice;

    @Column(name = "agreed_currency", length = 5)
    private String agreedCurrency;

    @Column(name = "agreed_delivery_terms", length = 255)
    private String agreedDeliveryTerms;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InquiryEntity() {}

    public InquiryEntity(
            UUID id,
            UUID harvestListingId,
            UUID buyerUserId,
            UUID sellerUserId,
            String cropType,
            String unitCode,
            String idempotencyKey
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.harvestListingId = harvestListingId;
        this.buyerUserId = buyerUserId;
        this.sellerUserId = sellerUserId;
        this.cropType = cropType;
        this.unitCode = unitCode;
        this.status = "OPEN";
        this.version = 1L;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHarvestListingId() { return harvestListingId; }
    public void setHarvestListingId(UUID harvestListingId) { this.harvestListingId = harvestListingId; }

    public UUID getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(UUID buyerUserId) { this.buyerUserId = buyerUserId; }

    public UUID getSellerUserId() { return sellerUserId; }
    public void setSellerUserId(UUID sellerUserId) { this.sellerUserId = sellerUserId; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getCurrentOfferId() { return currentOfferId; }
    public void setCurrentOfferId(UUID currentOfferId) { this.currentOfferId = currentOfferId; }

    public UUID getAgreedOfferId() { return agreedOfferId; }
    public void setAgreedOfferId(UUID agreedOfferId) { this.agreedOfferId = agreedOfferId; }

    public Instant getAgreedAt() { return agreedAt; }
    public void setAgreedAt(Instant agreedAt) { this.agreedAt = agreedAt; }

    public BigDecimal getAgreedQuantity() { return agreedQuantity; }
    public void setAgreedQuantity(BigDecimal agreedQuantity) { this.agreedQuantity = agreedQuantity; }

    public BigDecimal getAgreedUnitPrice() { return agreedUnitPrice; }
    public void setAgreedUnitPrice(BigDecimal agreedUnitPrice) { this.agreedUnitPrice = agreedUnitPrice; }

    public String getAgreedCurrency() { return agreedCurrency; }
    public void setAgreedCurrency(String agreedCurrency) { this.agreedCurrency = agreedCurrency; }

    public String getAgreedDeliveryTerms() { return agreedDeliveryTerms; }
    public void setAgreedDeliveryTerms(String agreedDeliveryTerms) { this.agreedDeliveryTerms = agreedDeliveryTerms; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
