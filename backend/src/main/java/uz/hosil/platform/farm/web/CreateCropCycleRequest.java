package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCropCycleRequest(
        UUID id,

        @NotNull(message = "Field ID is required")
        UUID fieldId,

        @NotBlank(message = "Crop type is required")
        String cropType,

        String variety,

        @NotNull(message = "Planting date is required")
        LocalDate plantingDate,

        LocalDate expectedHarvestDate
) {}
