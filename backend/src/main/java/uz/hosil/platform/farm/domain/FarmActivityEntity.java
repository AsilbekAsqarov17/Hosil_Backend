package uz.hosil.platform.farm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing daily agronomic operations and logs (docs/04_domain_model.md §4.2 Table 12).
 * Designed for offline-first replication and optimistic versioning.
 */
@Entity
@Table(name = "farm_activities")
public class FarmActivityEntity {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "crop_cycle_id")
    private UUID cropCycleId;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "activity_timestamp", nullable = false)
    private Instant activityTimestamp;

    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "sync_version", nullable = false)
    private Long syncVersion = 1L;

    @Column(name = "client_mutation_id", length = 100)
    private String clientMutationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FarmActivityEntity() {}

    public FarmActivityEntity(UUID id, UUID farmId, UUID cropCycleId, UUID fieldId, UUID recordedByUserId,
                              String activityType, Instant activityTimestamp, BigDecimal quantity,
                              String unit, String notes, Long syncVersion, String clientMutationId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.farmId = farmId;
        this.cropCycleId = cropCycleId;
        this.fieldId = fieldId;
        this.recordedByUserId = recordedByUserId;
        this.activityType = activityType;
        this.activityTimestamp = activityTimestamp != null ? activityTimestamp : Instant.now();
        this.quantity = quantity;
        this.unit = unit;
        this.notes = notes;
        this.syncVersion = syncVersion != null ? syncVersion : 1L;
        this.clientMutationId = clientMutationId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.activityTimestamp == null) {
            this.activityTimestamp = now;
        }
        if (this.syncVersion == null) {
            this.syncVersion = 1L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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

    public UUID getCropCycleId() {
        return cropCycleId;
    }

    public void setCropCycleId(UUID cropCycleId) {
        this.cropCycleId = cropCycleId;
    }

    public UUID getFieldId() {
        return fieldId;
    }

    public void setFieldId(UUID fieldId) {
        this.fieldId = fieldId;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public void setRecordedByUserId(UUID recordedByUserId) {
        this.recordedByUserId = recordedByUserId;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public Instant getActivityTimestamp() {
        return activityTimestamp;
    }

    public void setActivityTimestamp(Instant activityTimestamp) {
        this.activityTimestamp = activityTimestamp;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(Long syncVersion) {
        this.syncVersion = syncVersion;
    }

    public String getClientMutationId() {
        return clientMutationId;
    }

    public void setClientMutationId(String clientMutationId) {
        this.clientMutationId = clientMutationId;
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
}
