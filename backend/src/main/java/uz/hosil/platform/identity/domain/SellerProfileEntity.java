package uz.hosil.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Commercial verification lifecycle for agricultural-input suppliers and marketplace sellers (DEC-004).
 */
@Entity
@Table(name = "seller_profiles")
public class SellerProfileEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "seller_type", nullable = false, length = 30)
    private String sellerType; // INDIVIDUAL_FARMER, COMMERCIAL_SUPPLIER, COOPERATIVE

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "inn", length = 20)
    private String inn;

    @Column(name = "verification_status", nullable = false, length = 25)
    private String verificationStatus; // UNVERIFIED, PENDING, VERIFIED, REJECTED

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SellerProfileEntity() {}

    public SellerProfileEntity(
            UUID id,
            UUID userId,
            String sellerType,
            String businessName,
            String inn,
            String verificationStatus,
            Instant verifiedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.sellerType = sellerType != null ? sellerType : "INDIVIDUAL_FARMER";
        this.businessName = businessName;
        this.inn = inn;
        this.verificationStatus = verificationStatus != null ? verificationStatus : "UNVERIFIED";
        this.verifiedAt = verifiedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSellerType() {
        return sellerType;
    }

    public void setSellerType(String sellerType) {
        this.sellerType = sellerType;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
