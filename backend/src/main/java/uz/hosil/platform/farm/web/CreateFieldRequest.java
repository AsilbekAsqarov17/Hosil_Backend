package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFieldRequest(
        UUID id,

        @NotBlank(message = "Field name is required")
        @Size(min = 1, max = 100, message = "Field name must be between 1 and 100 characters")
        String name,

        @NotBlank(message = "Area is required")
        @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Area must be a valid positive decimal")
        String area,

        @NotBlank(message = "Area unit is required")
        @Pattern(regexp = "^(HECTARE|SOTKA|SQM)$", message = "Area unit must be HECTARE, SOTKA, or SQM")
        String areaUnit,

        String boundaryGeojson,

        String notes
) {}
