package uz.hosil.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Multi-role assignment entity per PRD ID-002 and docs/04_domain_model.md §4.1.
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_roles_user_role", columnNames = {"user_id", "role_name"})
})
public class UserRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public UserRoleEntity() {
    }

    public UserRoleEntity(UUID userId, String roleName) {
        this.userId = userId;
        this.roleName = roleName;
    }

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
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

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }
}
