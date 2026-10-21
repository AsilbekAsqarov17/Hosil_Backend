package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Join entity mapping allowable measurement units per product category (DEC-009, Table 15).
 */
@Entity
@Table(name = "category_allowed_units")
public class CategoryAllowedUnitEntity {

    @EmbeddedId
    private CategoryAllowedUnitId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CategoryAllowedUnitEntity() {}

    public CategoryAllowedUnitEntity(UUID categoryId, String unitCode) {
        this.id = new CategoryAllowedUnitId(categoryId, unitCode);
    }

    public CategoryAllowedUnitId getId() {
        return id;
    }

    public void setId(CategoryAllowedUnitId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class CategoryAllowedUnitId implements Serializable {

        @Column(name = "category_id", nullable = false)
        private UUID categoryId;

        @Column(name = "unit_code", nullable = false, length = 20)
        private String unitCode;

        public CategoryAllowedUnitId() {}

        public CategoryAllowedUnitId(UUID categoryId, String unitCode) {
            this.categoryId = categoryId;
            this.unitCode = unitCode;
        }

        public UUID getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(UUID categoryId) {
            this.categoryId = categoryId;
        }

        public String getUnitCode() {
            return unitCode;
        }

        public void setUnitCode(String unitCode) {
            this.unitCode = unitCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CategoryAllowedUnitId that = (CategoryAllowedUnitId) o;
            return Objects.equals(categoryId, that.categoryId) && Objects.equals(unitCode, that.unitCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId, unitCode);
        }
    }
}
