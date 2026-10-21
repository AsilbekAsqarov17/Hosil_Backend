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
 * Commercial input supplier offer linked to a master catalog product (Table 17, DEC-005, MKT-003).
 */
@Entity
@Table(name = "seller_offers")
public class SellerOfferEntity {

    @Id
    private UUID id;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "catalog_product_id", nullable = false)
    private UUID catalogProductId;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "available_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal availableQuantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQuantity;

    @Column(name = "sold_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal soldQuantity = BigDecimal.ZERO;

    @Column(name = "min_order_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal minOrderQuantity;

    @Column(name = "step_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal stepQuantity;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // DRAFT, ACTIVE, PAUSED, OUT_OF_STOCK, ARCHIVED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SellerOfferEntity() {}

    public SellerOfferEntity(
            UUID id,
            UUID sellerUserId,
            UUID catalogProductId,
            BigDecimal unitPrice,
            String currency,
            String unitCode,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal minOrderQuantity,
            BigDecimal stepQuantity,
            String region,
            String district,
            String notes,
            String status
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.sellerUserId = sellerUserId;
        this.catalogProductId = catalogProductId;
        this.unitPrice = unitPrice;
        this.currency = currency != null ? currency : "UZS";
        this.unitCode = unitCode;
        this.availableQuantity = availableQuantity != null ? availableQuantity : BigDecimal.ZERO;
        this.reservedQuantity = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        this.minOrderQuantity = minOrderQuantity != null ? minOrderQuantity : BigDecimal.ONE;
        this.stepQuantity = stepQuantity != null ? stepQuantity : BigDecimal.ONE;
        this.region = region;
        this.district = district;
        this.notes = notes;
        this.status = status != null ? status : "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public UUID getCatalogProductId() {
        return catalogProductId;
    }

    public void setCatalogProductId(UUID catalogProductId) {
        this.catalogProductId = catalogProductId;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(BigDecimal availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(BigDecimal reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public BigDecimal getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(BigDecimal soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public BigDecimal getMinOrderQuantity() {
        return minOrderQuantity;
    }

    public void setMinOrderQuantity(BigDecimal minOrderQuantity) {
        this.minOrderQuantity = minOrderQuantity;
    }

    public BigDecimal getStepQuantity() {
        return stepQuantity;
    }

    public void setStepQuantity(BigDecimal stepQuantity) {
        this.stepQuantity = stepQuantity;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Ledger conservation equation helper (DEC-005, DEC-010):
     * Total On-Hand = available_quantity + reserved_quantity + sold_quantity
     */
    public BigDecimal getTotalOnHandQuantity() {
        BigDecimal avail = availableQuantity != null ? availableQuantity : BigDecimal.ZERO;
        BigDecimal res = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        BigDecimal sold = soldQuantity != null ? soldQuantity : BigDecimal.ZERO;
        return avail.add(res).add(sold);
    }
}
