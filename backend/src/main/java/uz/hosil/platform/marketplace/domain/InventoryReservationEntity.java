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
 * Temporary atomic stock hold created during wholesale order placement (Table 20).
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservationEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "harvest_listing_id")
    private UUID harvestListingId;

    @Column(name = "seller_offer_id")
    private UUID sellerOfferId;

    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQuantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InventoryReservationEntity() {}

    public InventoryReservationEntity(
            UUID id,
            UUID orderId,
            UUID harvestListingId,
            BigDecimal reservedQuantity,
            String unitCode,
            Instant expiresAt,
            String status
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.harvestListingId = harvestListingId;
        this.sellerOfferId = null;
        this.reservedQuantity = reservedQuantity;
        this.unitCode = unitCode;
        this.expiresAt = expiresAt;
        this.status = status != null ? status : "ACTIVE";
    }

    public InventoryReservationEntity(
            UUID id,
            UUID orderId,
            BigDecimal reservedQuantity,
            String unitCode,
            Instant expiresAt,
            String status,
            UUID sellerOfferId
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.harvestListingId = null;
        this.sellerOfferId = sellerOfferId;
        this.reservedQuantity = reservedQuantity;
        this.unitCode = unitCode;
        this.expiresAt = expiresAt;
        this.status = status != null ? status : "ACTIVE";
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

    public UUID getHarvestListingId() {
        return harvestListingId;
    }

    public void setHarvestListingId(UUID harvestListingId) {
        this.harvestListingId = harvestListingId;
    }

    public UUID getSellerOfferId() {
        return sellerOfferId;
    }

    public void setSellerOfferId(UUID sellerOfferId) {
        this.sellerOfferId = sellerOfferId;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(BigDecimal reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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
}
