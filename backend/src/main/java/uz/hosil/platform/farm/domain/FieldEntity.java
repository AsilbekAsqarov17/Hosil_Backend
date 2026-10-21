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
 * Entity representing physical land parcels/plots (dala) belonging to a farm (docs/04_domain_model.md §4.2 Table 10).
 */
@Entity
@Table(name = "fields")
public class FieldEntity {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "area", nullable = false, precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "area_unit", nullable = false, length = 20)
    private String areaUnit = "HECTARE";

    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeojson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FieldEntity() {}

    public FieldEntity(UUID id, UUID farmId, String name, BigDecimal area, String areaUnit, String boundaryGeojson, String notes) {
        this.id = id != null ? id : UUID.randomUUID();
        this.farmId = farmId;
        this.name = name;
        this.area = area;
        this.areaUnit = areaUnit != null ? areaUnit : "HECTARE";
        this.boundaryGeojson = boundaryGeojson;
        this.notes = notes;
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
        if (this.areaUnit == null) {
            this.areaUnit = "HECTARE";
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getArea() {
        return area;
    }

    public void setArea(BigDecimal area) {
        this.area = area;
    }

    public String getAreaUnit() {
        return areaUnit;
    }

    public void setAreaUnit(String areaUnit) {
        this.areaUnit = areaUnit;
    }

    public String getBoundaryGeojson() {
        return boundaryGeojson;
    }

    public void setBoundaryGeojson(String boundaryGeojson) {
        this.boundaryGeojson = boundaryGeojson;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
