package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for creating a physical harvest batch (Endpoint 16).
 */
public record CreateHarvestBatchRequest(
        UUID id,
        @NotNull(message = "cropCycleId bo'sh bo'lmasligi kerak")
        UUID cropCycleId,
        @NotBlank(message = "cropType bo'sh bo'lmasligi kerak")
        String cropType,
        String qualityGrade,
        @NotBlank(message = "recordedQuantity bo'sh bo'lmasligi kerak")
        String recordedQuantity,
        @NotBlank(message = "unitCode bo'sh bo'lmasligi kerak")
        String unitCode,
        @NotNull(message = "harvestDate bo'sh bo'lmasligi kerak")
        LocalDate harvestDate,
        String storageLocationNotes,
        String clientBatchId
) {}
