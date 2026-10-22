package uz.hosil.platform.reputation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transaction-gated review entity per docs/12_reputation_and_reviews.md §7.1.
 */
@Entity
@Table(name = "reviews")
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_listing_id")
    private UUID targetListingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 30)
    private ReviewType reviewType;

    @Column(name = "overall_rating", nullable = false)
    private short overallRating;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_scores")
    private String dimensionScores;

    @Column(name = "commentary", length = 1000)
    private String commentary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.BLIND_PENDING;

    @Column(name = "is_counterparty_blind", nullable = false)
    private boolean counterpartyBlind = true;

    @Column(name = "blind_until")
    private Instant blindUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ReviewEntity() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
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

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(UUID orderItemId) {
        this.orderItemId = orderItemId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public void setAuthorId(UUID authorId) {
        this.authorId = authorId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public UUID getTargetListingId() {
        return targetListingId;
    }

    public void setTargetListingId(UUID targetListingId) {
        this.targetListingId = targetListingId;
    }

    public ReviewType getReviewType() {
        return reviewType;
    }

    public void setReviewType(ReviewType reviewType) {
        this.reviewType = reviewType;
    }

    public int getOverallRating() {
        return overallRating;
    }

    public void setOverallRating(int overallRating) {
        this.overallRating = (short) overallRating;
    }

    public String getDimensionScores() {
        return dimensionScores;
    }

    public void setDimensionScores(String dimensionScores) {
        this.dimensionScores = dimensionScores;
    }

    public String getCommentary() {
        return commentary;
    }

    public void setCommentary(String commentary) {
        this.commentary = commentary;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public boolean isCounterpartyBlind() {
        return counterpartyBlind;
    }

    public void setCounterpartyBlind(boolean counterpartyBlind) {
        this.counterpartyBlind = counterpartyBlind;
    }

    public Instant getBlindUntil() {
        return blindUntil;
    }

    public void setBlindUntil(Instant blindUntil) {
        this.blindUntil = blindUntil;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
