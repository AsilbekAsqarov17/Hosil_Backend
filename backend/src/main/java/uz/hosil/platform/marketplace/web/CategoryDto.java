package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.CategoryEntity;

import java.util.UUID;

/**
 * DTO representing an agricultural product category.
 */
public record CategoryDto(
        UUID id,
        UUID parentId,
        String code,
        String uzName,
        String ruName,
        Integer sortOrder,
        String categoryType,
        Boolean isActive
) {
    public static CategoryDto fromEntity(CategoryEntity entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getParentId(),
                entity.getCode(),
                entity.getUzName(),
                entity.getRuName(),
                entity.getSortOrder(),
                entity.getCategoryType(),
                entity.getIsActive()
        );
    }
}
