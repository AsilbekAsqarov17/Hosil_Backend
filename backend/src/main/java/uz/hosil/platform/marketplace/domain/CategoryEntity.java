package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "uz_name", nullable = false, length = 100)
    private String uzName;

    @Column(name = "ru_name", nullable = false, length = 100)
    private String ruName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "category_type", nullable = false, length = 30)
    private String categoryType; // AGRICULTURAL_INPUT, HARVEST_PRODUCE

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public CategoryEntity() {}

    public CategoryEntity(UUID id, UUID parentId, String code, String uzName, String ruName, Integer sortOrder) {
        this(id, parentId, code, uzName, ruName, sortOrder, "AGRICULTURAL_INPUT", true);
    }

    public CategoryEntity(UUID id, UUID parentId, String code, String uzName, String ruName, Integer sortOrder, String categoryType, Boolean isActive) {
        this.id = id != null ? id : UUID.randomUUID();
        this.parentId = parentId;
        this.code = code;
        this.uzName = uzName;
        this.ruName = ruName;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.categoryType = categoryType != null ? categoryType : "AGRICULTURAL_INPUT";
        this.isActive = isActive != null ? isActive : true;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getUzName() { return uzName; }
    public void setUzName(String uzName) { this.uzName = uzName; }

    public String getRuName() { return ruName; }
    public void setRuName(String ruName) { this.ruName = ruName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
