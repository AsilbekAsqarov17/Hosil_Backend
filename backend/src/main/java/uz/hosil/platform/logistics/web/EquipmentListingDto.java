package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.EquipmentListingEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EquipmentListingDto(
        UUID id,
        UUID ownerUserId,
        String category,
        String title,
        String modelName,
        String manufacturer,
        Integer yearOfManufacture,
        String condition,
        String description,
        String specifications,
        String region,
        String district,
        String imageUrls,
        BigDecimal dailyRate,
        BigDecimal monthlyRate,
        BigDecimal marketValue,
        String currency,
        BigDecimal minDownPaymentPercent,
        Integer minTermMonths,
        Integer maxTermMonths,
        String availabilityStatus,
        String publicationStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static EquipmentListingDto fromEntity(EquipmentListingEntity e) {
        return new EquipmentListingDto(
                e.getId(),
                e.getOwnerUserId(),
                e.getCategory(),
                e.getTitle(),
                e.getModelName(),
                e.getManufacturer(),
                e.getYearOfManufacture(),
                e.getCondition(),
                e.getDescription(),
                e.getSpecifications(),
                e.getRegion(),
                e.getDistrict(),
                e.getImageUrls(),
                e.getDailyRate(),
                e.getMonthlyRate(),
                e.getMarketValue(),
                e.getCurrency(),
                e.getMinDownPaymentPercent(),
                e.getMinTermMonths(),
                e.getMaxTermMonths(),
                e.getAvailabilityStatus(),
                e.getPublicationStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
