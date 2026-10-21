package uz.hosil.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * State machine entity for phone SMS OTP verification challenges.
 * Source of Truth: docs/04_domain_model.md §4.1.
 */
@Entity
@Table(name = "otp_challenges")
public class OtpChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "challenge_hash", nullable = false, length = 128)
    private String challengeHash;

    @Column(name = "salt", length = 32)
    private String salt;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "is_consumed", nullable = false)
    private boolean isConsumed;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OtpChallengeEntity() {
    }

    public OtpChallengeEntity(String phoneNumber, String challengeHash, String salt, Instant expiresAt) {
        this.phoneNumber = phoneNumber;
        this.challengeHash = challengeHash;
        this.salt = salt;
        this.expiresAt = expiresAt;
        this.attemptsCount = 0;
        this.maxAttempts = 5;
        this.verified = false;
        this.isConsumed = false;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (maxAttempts == 0) {
            maxAttempts = 5;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getChallengeHash() {
        return challengeHash;
    }

    public void setChallengeHash(String challengeHash) {
        this.challengeHash = challengeHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(int attemptsCount) {
        this.attemptsCount = attemptsCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isConsumed() {
        return isConsumed;
    }

    public void setConsumed(boolean consumed) {
        isConsumed = consumed;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
