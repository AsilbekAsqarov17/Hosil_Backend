package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public record CreateFarmActivityRequest(
        UUID id,

        UUID cropCycleId,

        @NotNull(message = "Field ID is required")
        UUID fieldId,

        @NotBlank(message = "Activity type is required")
        @Pattern(regexp = "^(PLANTING|IRRIGATION|FERTILIZING|CHEMICAL_TREATMENT|PESTICIDE|WEEDING|PRUNING|INSPECTION|SCOUTING|HARVEST|HARVESTING|OTHER)$",
                message = "Invalid activity type")
        String activityType,

        @NotNull(message = "Activity timestamp is required")
        Instant activityTimestamp,

        @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,3})?$", message = "Quantity must be a valid positive decimal")
        String quantity,

        String unit,

        String notes,

        String clientMutationId,

        Long syncVersion
) {}
