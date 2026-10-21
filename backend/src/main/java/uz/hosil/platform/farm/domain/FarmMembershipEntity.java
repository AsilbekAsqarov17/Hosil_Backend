package uz.hosil.platform.farm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing farm membership and ABAC authorization (docs/04_domain_model.md §4.1 Table 8).
 */
@Entity
@Table(name = "farm_memberships")
public class FarmMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "permissions_mask", nullable = false, length = 255)
    private String permissionsMask = "READ";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FarmMembershipEntity() {}

    public FarmMembershipEntity(UUID farmId, UUID userId, String role, String permissionsMask, String status) {
        this.farmId = farmId;
        this.userId = userId;
        this.role = role;
        this.permissionsMask = permissionsMask != null ? permissionsMask : "READ";
        this.status = status != null ? status : "ACTIVE";
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.permissionsMask == null) {
            this.permissionsMask = "READ";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFarmId() {
        return farmId;
    }

    public void setFarmId(UUID farmId) {
        this.farmId = farmId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPermissionsMask() {
        return permissionsMask;
    }

    public void setPermissionsMask(String permissionsMask) {
        this.permissionsMask = permissionsMask;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
