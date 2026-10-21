package uz.hosil.platform.farm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Physical bulk harvest batch recorded at field level.
 * Acts as the physical inventory foundation for marketplace listings (DEC-008).
 */
@Entity
@Table(name = "harvest_batches")
public class HarvestBatchEntity {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "crop_cycle_id")
    private UUID cropCycleId;

    @Column(name = "crop_type", nullable = false, length = 100)
    private String cropType;

    @Column(name = "quality_grade", length = 30)
    private String qualityGrade;

    @Column(name = "recorded_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal recordedQuantity;

    @Column(name = "unallocated_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal unallocatedQuantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "harvest_date", nullable = false)
    private LocalDate harvestDate;

    @Column(name = "storage_location_notes", length = 255)
    private String storageLocationNotes;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "client_batch_id", length = 100)
    private String clientBatchId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "sync_version", nullable = false)
    private Long syncVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HarvestBatchEntity() {}

    public HarvestBatchEntity(
            UUID id,
            UUID farmId,
            UUID cropCycleId,
            String cropType,
            String qualityGrade,
            BigDecimal recordedQuantity,
            BigDecimal unallocatedQuantity,
            String unitCode,
            LocalDate harvestDate,
            String storageLocationNotes,
            String status,
            String clientBatchId,
            String idempotencyKey,
            Long syncVersion
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.farmId = farmId;
        this.cropCycleId = cropCycleId;
        this.cropType = cropType;
        this.qualityGrade = qualityGrade;
        this.recordedQuantity = recordedQuantity;
        this.unallocatedQuantity = unallocatedQuantity;
        this.unitCode = unitCode;
        this.harvestDate = harvestDate;
        this.storageLocationNotes = storageLocationNotes;
        this.status = status != null ? status : "RECORDED";
        this.clientBatchId = clientBatchId;
        this.idempotencyKey = idempotencyKey;
        this.syncVersion = syncVersion != null ? syncVersion : 1L;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getQualityGrade() { return qualityGrade; }
    public void setQualityGrade(String qualityGrade) { this.qualityGrade = qualityGrade; }

    public BigDecimal getRecordedQuantity() { return recordedQuantity; }
    public void setRecordedQuantity(BigDecimal recordedQuantity) { this.recordedQuantity = recordedQuantity; }

    public BigDecimal getUnallocatedQuantity() { return unallocatedQuantity; }
    public void setUnallocatedQuantity(BigDecimal unallocatedQuantity) { this.unallocatedQuantity = unallocatedQuantity; }

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }

    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }

    public String getStorageLocationNotes() { return storageLocationNotes; }
    public void setStorageLocationNotes(String storageLocationNotes) { this.storageLocationNotes = storageLocationNotes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClientBatchId() { return clientBatchId; }
    public void setClientBatchId(String clientBatchId) { this.clientBatchId = clientBatchId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getSyncVersion() { return syncVersion; }
    public void setSyncVersion(Long syncVersion) { this.syncVersion = syncVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
