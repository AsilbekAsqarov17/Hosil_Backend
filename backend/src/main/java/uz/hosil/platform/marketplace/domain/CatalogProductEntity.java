package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Standardized master catalog product for agricultural inputs (fertilizers, seeds, machinery) (Table 16, MKT-003).
 */
@Entity
@Table(name = "catalog_products")
public class CatalogProductEntity {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name_uz", nullable = false)
    private String nameUz;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "active_substance")
    private String activeSubstance;

    @Column(name = "default_unit_code", nullable = false, length = 20)
    private String defaultUnitCode;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CatalogProductEntity() {}

    public CatalogProductEntity(
            UUID id,
            UUID categoryId,
            String nameUz,
            String nameRu,
            String brandName,
            String activeSubstance,
            String defaultUnitCode,
            String description,
            Boolean isActive
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.categoryId = categoryId;
        this.nameUz = nameUz;
        this.nameRu = nameRu;
        this.brandName = brandName;
        this.activeSubstance = activeSubstance;
        this.defaultUnitCode = defaultUnitCode;
        this.description = description;
        this.isActive = isActive != null ? isActive : true;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getNameUz() {
        return nameUz;
    }

    public void setNameUz(String nameUz) {
        this.nameUz = nameUz;
    }

    public String getNameRu() {
        return nameRu;
    }

    public void setNameRu(String nameRu) {
        this.nameRu = nameRu;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getActiveSubstance() {
        return activeSubstance;
    }

    public void setActiveSubstance(String activeSubstance) {
        this.activeSubstance = activeSubstance;
    }

    public String getDefaultUnitCode() {
        return defaultUnitCode;
    }

    public void setDefaultUnitCode(String defaultUnitCode) {
        this.defaultUnitCode = defaultUnitCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
