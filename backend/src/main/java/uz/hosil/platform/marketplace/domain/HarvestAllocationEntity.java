package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Explicit harvest allocation ledger entity linking private harvest batch to public listing (DEC-008).
 */
@Entity
@Table(name = "harvest_allocations")
public class HarvestAllocationEntity {

    @Id
    private UUID id;

    @Column(name = "harvest_batch_id", nullable = false)
    private UUID harvestBatchId;

    @Column(name = "harvest_listing_id", nullable = false)
    private UUID harvestListingId;

    @Column(name = "allocated_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal allocatedQuantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HarvestAllocationEntity() {}

    public HarvestAllocationEntity(
            UUID id,
            UUID harvestBatchId,
            UUID harvestListingId,
            BigDecimal allocatedQuantity,
            String unit,
            String status,
            Instant allocatedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.harvestBatchId = harvestBatchId;
        this.harvestListingId = harvestListingId;
        this.allocatedQuantity = allocatedQuantity;
        this.unit = unit;
        this.status = status != null ? status : "ACTIVE";
        this.allocatedAt = allocatedAt != null ? allocatedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHarvestBatchId() { return harvestBatchId; }
    public void setHarvestBatchId(UUID harvestBatchId) { this.harvestBatchId = harvestBatchId; }

    public UUID getHarvestListingId() { return harvestListingId; }
    public void setHarvestListingId(UUID harvestListingId) { this.harvestListingId = harvestListingId; }

    public BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(BigDecimal allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(Instant allocatedAt) { this.allocatedAt = allocatedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
