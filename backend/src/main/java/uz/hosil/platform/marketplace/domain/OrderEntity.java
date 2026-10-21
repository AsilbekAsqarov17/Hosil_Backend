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
 * Master purchase order representing a wholesale agricultural trade contract (DEC-006, MKT-010).
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "inquiry_id")
    private UUID inquiryId;

    @Column(name = "agreed_offer_id")
    private UUID agreedOfferId;

    @Column(name = "harvest_listing_id")
    private UUID harvestListingId;

    @Column(name = "order_flow_type", nullable = false, length = 30)
    private String orderFlowType;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 5)
    private String currency;

    @Column(name = "status", nullable = false, length = 35)
    private String status;

    @Column(name = "delivery_type", nullable = false, length = 30)
    private String deliveryType;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_terms", length = 255)
    private String deliveryTerms;

    @Column(name = "notes")
    private String notes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "confirmation_expires_at")
    private Instant confirmationExpiresAt;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderEntity() {}

    public OrderEntity(
            UUID id,
            String orderNumber,
            UUID buyerUserId,
            UUID sellerUserId,
            UUID inquiryId,
            UUID agreedOfferId,
            UUID harvestListingId,
            String orderFlowType,
            BigDecimal totalAmount,
            String currency,
            String status,
            String deliveryType,
            String deliveryAddress,
            String deliveryTerms,
            String notes,
            Instant confirmationExpiresAt,
            String idempotencyKey
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderNumber = orderNumber;
        this.buyerUserId = buyerUserId;
        this.sellerUserId = sellerUserId;
        this.inquiryId = inquiryId;
        this.agreedOfferId = agreedOfferId;
        this.harvestListingId = harvestListingId;
        this.orderFlowType = orderFlowType != null ? orderFlowType : "HARVEST_PURCHASE_REQUEST";
        this.totalAmount = totalAmount;
        this.currency = currency != null ? currency : "UZS";
        this.status = status != null ? status : "AWAITING_SELLER_CONFIRMATION";
        this.deliveryType = deliveryType != null ? deliveryType : "BUYER_ARRANGES";
        this.deliveryAddress = deliveryAddress;
        this.deliveryTerms = deliveryTerms;
        this.notes = notes;
        this.confirmationExpiresAt = confirmationExpiresAt;
        this.idempotencyKey = idempotencyKey;
        this.version = 1L;
    }

    public OrderEntity(
            UUID id,
            String orderNumber,
            UUID buyerUserId,
            UUID sellerUserId,
            String orderFlowType,
            BigDecimal totalAmount,
            String currency,
            String status,
            String deliveryType,
            String deliveryAddress,
            String deliveryTerms,
            String notes,
            Instant paymentExpiresAt,
            String idempotencyKey
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderNumber = orderNumber;
        this.buyerUserId = buyerUserId;
        this.sellerUserId = sellerUserId;
        this.inquiryId = null;
        this.agreedOfferId = null;
        this.harvestListingId = null;
        this.orderFlowType = orderFlowType != null ? orderFlowType : "INPUT_DIRECT_CHECKOUT";
        this.totalAmount = totalAmount;
        this.currency = currency != null ? currency : "UZS";
        this.status = status != null ? status : "PENDING_PAYMENT";
        this.deliveryType = deliveryType != null ? deliveryType : "BUYER_ARRANGES";
        this.deliveryAddress = deliveryAddress;
        this.deliveryTerms = deliveryTerms;
        this.notes = notes;
        this.paymentExpiresAt = paymentExpiresAt;
        this.idempotencyKey = idempotencyKey;
        this.version = 1L;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public UUID getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(UUID buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public UUID getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(UUID sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public UUID getInquiryId() {
        return inquiryId;
    }

    public void setInquiryId(UUID inquiryId) {
        this.inquiryId = inquiryId;
    }

    public UUID getAgreedOfferId() {
        return agreedOfferId;
    }

    public void setAgreedOfferId(UUID agreedOfferId) {
        this.agreedOfferId = agreedOfferId;
    }

    public UUID getHarvestListingId() {
        return harvestListingId;
    }

    public void setHarvestListingId(UUID harvestListingId) {
        this.harvestListingId = harvestListingId;
    }

    public String getOrderFlowType() {
        return orderFlowType;
    }

    public void setOrderFlowType(String orderFlowType) {
        this.orderFlowType = orderFlowType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public String getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(String deliveryType) {
        this.deliveryType = deliveryType;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public void setDeliveryTerms(String deliveryTerms) {
        this.deliveryTerms = deliveryTerms;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getConfirmationExpiresAt() {
        return confirmationExpiresAt;
    }

    public void setConfirmationExpiresAt(Instant confirmationExpiresAt) {
        this.confirmationExpiresAt = confirmationExpiresAt;
    }

    public Instant getPaymentExpiresAt() {
        return paymentExpiresAt;
    }

    public void setPaymentExpiresAt(Instant paymentExpiresAt) {
        this.paymentExpiresAt = paymentExpiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
