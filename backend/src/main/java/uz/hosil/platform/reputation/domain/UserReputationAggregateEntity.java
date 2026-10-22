package uz.hosil.platform.reputation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Precalculated aggregate reputation entity per docs/12_reputation_and_reviews.md §7.1 & §4.1.
 */
@Entity
@Table(name = "user_reputation_aggregates")
public class UserReputationAggregateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_context", nullable = false, length = 20)
    private String roleContext;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    private int totalReviews = 0;

    @Column(name = "total_completed_orders", nullable = false)
    private int totalCompletedOrders = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_averages")
    private String dimensionAverages;

    @Column(name = "last_calculated_at", nullable = false)
    private Instant lastCalculatedAt;

    public UserReputationAggregateEntity() {
    }

    public UserReputationAggregateEntity(UUID userId, String roleContext) {
        this.userId = userId;
        this.roleContext = roleContext;
        this.averageRating = BigDecimal.ZERO;
        this.totalReviews = 0;
        this.totalCompletedOrders = 0;
        this.lastCalculatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.lastCalculatedAt = Instant.now();
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

    public String getRoleContext() {
        return roleContext;
    }

    public void setRoleContext(String roleContext) {
        this.roleContext = roleContext;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(int totalReviews) {
        this.totalReviews = totalReviews;
    }

    public int getTotalCompletedOrders() {
        return totalCompletedOrders;
    }

    public void setTotalCompletedOrders(int totalCompletedOrders) {
        this.totalCompletedOrders = totalCompletedOrders;
    }

    public String getDimensionAverages() {
        return dimensionAverages;
    }

    public void setDimensionAverages(String dimensionAverages) {
        this.dimensionAverages = dimensionAverages;
    }

    public Instant getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    public void setLastCalculatedAt(Instant lastCalculatedAt) {
        this.lastCalculatedAt = lastCalculatedAt;
    }
}
