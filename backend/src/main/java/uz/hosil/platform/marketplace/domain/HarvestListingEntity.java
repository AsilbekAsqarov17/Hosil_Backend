package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wholesale agricultural harvest listing published by a farmer on Hosil Bozor (DEC-008, MKT-004).
 */
@Entity
@Table(name = "harvest_listings")
public class HarvestListingEntity {

    @Id
    private UUID id;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "harvest_batch_id")
    private UUID harvestBatchId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "crop_type", nullable = false, length = 100)
    private String cropType;

    @Column(name = "quality_grade", length = 30)
    private String qualityGrade;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @Column(name = "pricing_type", nullable = false, length = 20)
    private String pricingType;

    @Column(name = "is_rfq_enabled", nullable = false)
    private Boolean isRfqEnabled;

    @Column(name = "allocated_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal allocatedQuantity;

    @Column(name = "available_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal availableQuantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQuantity;

    @Column(name = "sold_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal soldQuantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "min_order_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal minOrderQuantity;

    @Column(name = "step_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal stepQuantity;

    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "status", nullable = false, length = 25)
    private String status;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HarvestListingEntity() {}

    public HarvestListingEntity(
            UUID id,
            UUID sellerUserId,
            UUID harvestBatchId,
            UUID categoryId,
            String title,
            String description,
            String cropType,
            String qualityGrade,
            BigDecimal unitPrice,
            String currency,
            String pricingType,
            Boolean isRfqEnabled,
            BigDecimal allocatedQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal soldQuantity,
            String unitCode,
            BigDecimal minOrderQuantity,
            BigDecimal stepQuantity,
            String region,
            String district,
            String status,
            String idempotencyKey,
            Instant publishedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.sellerUserId = sellerUserId;
        this.harvestBatchId = harvestBatchId;
        this.categoryId = categoryId;
        this.title = title != null ? title : cropType;
        this.description = description;
        this.cropType = cropType;
        this.qualityGrade = qualityGrade;
        this.unitPrice = unitPrice;
        this.currency = currency != null ? currency : "UZS";
        this.pricingType = pricingType != null ? pricingType : "FIXED";
        this.isRfqEnabled = isRfqEnabled != null ? isRfqEnabled : Boolean.FALSE;
        this.allocatedQuantity = allocatedQuantity;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        this.soldQuantity = soldQuantity != null ? soldQuantity : BigDecimal.ZERO;
        this.unitCode = unitCode;
        this.minOrderQuantity = minOrderQuantity;
        this.stepQuantity = stepQuantity;
        this.region = region;
        this.district = district;
        this.status = status != null ? status : "PUBLISHED";
        this.idempotencyKey = idempotencyKey;
        this.publishedAt = publishedAt != null ? publishedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSellerUserId() { return sellerUserId; }
    public void setSellerUserId(UUID sellerUserId) { this.sellerUserId = sellerUserId; }

    public UUID getHarvestBatchId() { return harvestBatchId; }
    public void setHarvestBatchId(UUID harvestBatchId) { this.harvestBatchId = harvestBatchId; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getQualityGrade() { return qualityGrade; }
    public void setQualityGrade(String qualityGrade) { this.qualityGrade = qualityGrade; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPricingType() { return pricingType; }
    public void setPricingType(String pricingType) { this.pricingType = pricingType; }

    public Boolean getIsRfqEnabled() { return isRfqEnabled; }
    public void setIsRfqEnabled(Boolean isRfqEnabled) { this.isRfqEnabled = isRfqEnabled; }

    public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(BigDecimal allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }

    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }

    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public BigDecimal getSoldQuantity() { return soldQuantity; }
    public void setSoldQuantity(BigDecimal soldQuantity) { this.soldQuantity = soldQuantity; }

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }

    public BigDecimal getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(BigDecimal minOrderQuantity) { this.minOrderQuantity = minOrderQuantity; }

    public BigDecimal getStepQuantity() { return stepQuantity; }
    public void setStepQuantity(BigDecimal stepQuantity) { this.stepQuantity = stepQuantity; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
