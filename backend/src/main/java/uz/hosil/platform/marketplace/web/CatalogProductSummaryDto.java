package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.CatalogProductEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Summary DTO for paginated catalog product discovery.
 */
public record CatalogProductSummaryDto(
        UUID id,
        UUID categoryId,
        String categoryNameUz,
        String categoryNameRu,
        String nameUz,
        String nameRu,
        String brandName,
        String activeSubstance,
        String defaultUnitCode,
        long activeOffersCount,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency
) {
    public static CatalogProductSummaryDto fromEntity(
            CatalogProductEntity product,
            String categoryNameUz,
            String categoryNameRu,
            long activeOffersCount,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency
    ) {
        return new CatalogProductSummaryDto(
                product.getId(),
                product.getCategoryId(),
                categoryNameUz,
                categoryNameRu,
                product.getNameUz(),
                product.getNameRu(),
                product.getBrandName(),
                product.getActiveSubstance(),
                product.getDefaultUnitCode(),
                activeOffersCount,
                minPrice,
                maxPrice,
                currency != null ? currency : "UZS"
        );
    }
}
