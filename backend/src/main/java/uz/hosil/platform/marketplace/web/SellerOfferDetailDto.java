package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.SellerOfferEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detailed DTO for seller managing their own input offers.
 */
public record SellerOfferDetailDto(
        UUID id,
        UUID sellerUserId,
        UUID catalogProductId,
        String productNameUz,
        String productNameRu,
        String brandName,
        BigDecimal unitPrice,
        String currency,
        String unitCode,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal minOrderQuantity,
        BigDecimal stepQuantity,
        String region,
        String district,
        String notes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SellerOfferDetailDto fromEntity(
            SellerOfferEntity entity,
            String productNameUz,
            String productNameRu,
            String brandName
    ) {
        return new SellerOfferDetailDto(
                entity.getId(),
                entity.getSellerUserId(),
                entity.getCatalogProductId(),
                productNameUz,
                productNameRu,
                brandName,
                entity.getUnitPrice(),
                entity.getCurrency(),
                entity.getUnitCode(),
                entity.getAvailableQuantity(),
                entity.getReservedQuantity(),
                entity.getMinOrderQuantity(),
                entity.getStepQuantity(),
                entity.getRegion(),
                entity.getDistrict(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
