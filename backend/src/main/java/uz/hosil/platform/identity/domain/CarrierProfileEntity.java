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
 * Carrier profile and verification entity for Hosil Logistika (Table 27, DEC-011, LOG-001).
 */
@Entity
@Table(name = "carrier_profiles")
public class CarrierProfileEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

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

    public CarrierProfileEntity() {}

    public CarrierProfileEntity(
            UUID id,
            UUID userId,
            String companyName,
            String licenseNumber,
            String verificationStatus,
            Instant verifiedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.companyName = companyName;
        this.licenseNumber = licenseNumber;
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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
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
