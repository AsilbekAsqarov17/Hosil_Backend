package uz.hosil.platform.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Long-lived authentication session and refresh token persistence entity.
 * Supports token rotation, device tracking, and token reuse detection.
 * Source of Truth: docs/04_domain_model.md §4.1 and V4 reconciliation.
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private boolean isRevoked;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Type(PostgresInetUserType.class)
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuthSessionEntity() {
    }

    public AuthSessionEntity(UUID userId, String tokenHash, Instant expiresAt, String deviceFingerprint, String userAgent, String ipAddress) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceFingerprint = deviceFingerprint;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.isRevoked = false;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastActiveAt == null) {
            lastActiveAt = now;
        }
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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return isRevoked;
    }

    public void setRevoked(boolean revoked) {
        isRevoked = revoked;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
