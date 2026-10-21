package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.SellerOfferEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public discovery DTO for active commercial seller offers (safe from internal business fields).
 */
public record PublicSellerOfferDto(
        UUID id,
        UUID catalogProductId,
        UUID sellerUserId,
        String sellerBusinessName,
        BigDecimal unitPrice,
        String currency,
        String unitCode,
        BigDecimal availableQuantity,
        BigDecimal minOrderQuantity,
        BigDecimal stepQuantity,
        String region,
        String district,
        String notes,
        Instant updatedAt
) {
    public static PublicSellerOfferDto fromEntity(SellerOfferEntity entity, String sellerBusinessName) {
        return new PublicSellerOfferDto(
                entity.getId(),
                entity.getCatalogProductId(),
                entity.getSellerUserId(),
                sellerBusinessName != null ? sellerBusinessName : "Tasdiqlangan taʼminotchi",
                entity.getUnitPrice(),
                entity.getCurrency(),
                entity.getUnitCode(),
                entity.getAvailableQuantity(),
                entity.getMinOrderQuantity(),
                entity.getStepQuantity(),
                entity.getRegion(),
                entity.getDistrict(),
                entity.getNotes(),
                entity.getUpdatedAt()
        );
    }
}
