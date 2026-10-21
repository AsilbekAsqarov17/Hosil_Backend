package uz.hosil.platform.farm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an agricultural enterprise or private holding (docs/04_domain_model.md §4.2 Table 9).
 */
@Entity
@Table(name = "farms")
public class FarmEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "district", nullable = false, length = 50)
    private String district;

    @Column(name = "total_area_ha", precision = 10, scale = 2)
    private BigDecimal totalAreaHa;

    @Column(name = "area_unit", nullable = false, length = 20)
    private String areaUnit = "HECTARE";

    @Column(name = "legal_type", nullable = false, length = 50)
    private String legalType = "DEHQON";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FarmEntity() {}

    public FarmEntity(UUID ownerUserId, String name, String region, String district, BigDecimal totalAreaHa, String legalType) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.region = region;
        this.district = district;
        this.totalAreaHa = totalAreaHa;
        if (legalType != null) {
            this.legalType = legalType;
        }
    }

    @PrePersist
    protected void onCreate() {
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
        if (this.legalType == null) {
            this.legalType = "DEHQON";
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

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public BigDecimal getTotalAreaHa() {
        return totalAreaHa;
    }

    public void setTotalAreaHa(BigDecimal totalAreaHa) {
        this.totalAreaHa = totalAreaHa;
    }

    public String getAreaUnit() {
        return areaUnit;
    }

    public void setAreaUnit(String areaUnit) {
        this.areaUnit = areaUnit;
    }

    public String getLegalType() {
        return legalType;
    }

    public void setLegalType(String legalType) {
        this.legalType = legalType;
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
