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
 * Immutable purchase snapshot of harvest listing details at the time of order creation (Table 22).
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "harvest_listing_id")
    private UUID harvestListingId;

    @Column(name = "seller_offer_id")
    private UUID sellerOfferId;

    @Column(name = "catalog_product_id")
    private UUID catalogProductId;

    @Column(name = "item_snapshot_title", nullable = false)
    private String itemSnapshotTitle;

    @Column(name = "crop_type", length = 100)
    private String cropType;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItemEntity() {}

    public OrderItemEntity(
            UUID id,
            UUID orderId,
            UUID harvestListingId,
            String itemSnapshotTitle,
            String cropType,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount,
            String currency
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.harvestListingId = harvestListingId;
        this.sellerOfferId = null;
        this.catalogProductId = null;
        this.itemSnapshotTitle = itemSnapshotTitle;
        this.cropType = cropType;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.unitPrice = unitPrice;
        this.subtotalAmount = subtotalAmount;
        this.currency = currency != null ? currency : "UZS";
    }

    public OrderItemEntity(
            UUID id,
            UUID orderId,
            UUID sellerOfferId,
            UUID catalogProductId,
            String itemSnapshotTitle,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount,
            String currency
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.harvestListingId = null;
        this.sellerOfferId = sellerOfferId;
        this.catalogProductId = catalogProductId;
        this.itemSnapshotTitle = itemSnapshotTitle;
        this.cropType = null;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.unitPrice = unitPrice;
        this.subtotalAmount = subtotalAmount;
        this.currency = currency != null ? currency : "UZS";
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

    public UUID getCatalogProductId() {
        return catalogProductId;
    }

    public void setCatalogProductId(UUID catalogProductId) {
        this.catalogProductId = catalogProductId;
    }

    public String getItemSnapshotTitle() {
        return itemSnapshotTitle;
    }

    public void setItemSnapshotTitle(String itemSnapshotTitle) {
        this.itemSnapshotTitle = itemSnapshotTitle;
    }

    public String getCropType() {
        return cropType;
    }

    public void setCropType(String cropType) {
        this.cropType = cropType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
