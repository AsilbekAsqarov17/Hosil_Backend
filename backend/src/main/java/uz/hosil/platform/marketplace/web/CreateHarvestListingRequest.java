package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for publishing a harvest batch to Hosil Bozor (Endpoint 18).
 */
public record CreateHarvestListingRequest(
        @NotNull(message = "harvestBatchId bo'sh bo'lmasligi kerak")
        UUID harvestBatchId,
        @NotBlank(message = "cropType bo'sh bo'lmasligi kerak")
        String cropType,
        String qualityGrade,
        @NotBlank(message = "allocatedQuantity bo'sh bo'lmasligi kerak")
        String allocatedQuantity,
        @NotBlank(message = "unitCode bo'sh bo'lmasligi kerak")
        String unitCode,
        String unitPrice,
        String currency,
        Boolean isRfqEnabled,
        @NotBlank(message = "minOrderQuantity bo'sh bo'lmasligi kerak")
        String minOrderQuantity,
        @NotBlank(message = "stepQuantity bo'sh bo'lmasligi kerak")
        String stepQuantity,
        @NotBlank(message = "region bo'sh bo'lmasligi kerak")
        String region,
        String district,
        List<UUID> mediaFileIds
) {}
