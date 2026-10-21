package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.HarvestListingEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public commercial DTO for harvest listings published on Hosil Bozor.
 * Excludes private farm plots, GeoJSON boundaries, chemical treatment logs, worker identities, and notes.
 */
public record HarvestListingDto(
        UUID id,
        UUID sellerUserId,
        UUID harvestBatchId,
        String cropType,
        String qualityGrade,
        BigDecimal allocatedQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal soldQuantity,
        String unitCode,
        BigDecimal unitPrice,
        String currency,
        Boolean isRfqEnabled,
        BigDecimal minOrderQuantity,
        BigDecimal stepQuantity,
        String region,
        String district,
        String status,
        Instant publishedAt,
        List<UUID> mediaFileIds
) {
    public static HarvestListingDto fromEntity(HarvestListingEntity entity, List<UUID> mediaFileIds) {
        BigDecimal unitPrice = Boolean.TRUE.equals(entity.getIsRfqEnabled()) ? null : entity.getUnitPrice();
        return new HarvestListingDto(
                entity.getId(),
                entity.getSellerUserId(),
                entity.getHarvestBatchId(),
                entity.getCropType(),
                entity.getQualityGrade(),
                entity.getAllocatedQuantity(),
                entity.getAvailableQuantity(),
                entity.getReservedQuantity(),
                entity.getSoldQuantity(),
                entity.getUnitCode(),
                unitPrice,
                entity.getCurrency(),
                entity.getIsRfqEnabled(),
                entity.getMinOrderQuantity(),
                entity.getStepQuantity(),
                entity.getRegion(),
                entity.getDistrict(),
                entity.getStatus(),
                entity.getPublishedAt(),
                mediaFileIds != null ? mediaFileIds : List.of()
        );
    }
}
