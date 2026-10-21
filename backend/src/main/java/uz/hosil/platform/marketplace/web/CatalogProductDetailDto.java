package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.CatalogProductEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detailed DTO for catalog product details.
 */
public record CatalogProductDetailDto(
        UUID id,
        UUID categoryId,
        String categoryCode,
        String categoryNameUz,
        String categoryNameRu,
        String nameUz,
        String nameRu,
        String brandName,
        String activeSubstance,
        String defaultUnitCode,
        String description,
        Boolean isActive,
        long activeOffersCount,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency,
        Instant createdAt
) {
    public static CatalogProductDetailDto fromEntity(
            CatalogProductEntity product,
            String categoryCode,
            String categoryNameUz,
            String categoryNameRu,
            long activeOffersCount,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency
    ) {
        return new CatalogProductDetailDto(
                product.getId(),
                product.getCategoryId(),
                categoryCode,
                categoryNameUz,
                categoryNameRu,
                product.getNameUz(),
                product.getNameRu(),
                product.getBrandName(),
                product.getActiveSubstance(),
                product.getDefaultUnitCode(),
                product.getDescription(),
                product.getIsActive(),
                activeOffersCount,
                minPrice,
                maxPrice,
                currency != null ? currency : "UZS",
                product.getCreatedAt()
        );
    }
}
