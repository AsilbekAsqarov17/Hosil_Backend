package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateFieldRequest(
        @Size(min = 1, max = 100, message = "Field name must be between 1 and 100 characters")
        String name,

        @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Area must be a valid positive decimal")
        String area,

        @Pattern(regexp = "^(HECTARE|SOTKA|SQM)$", message = "Area unit must be HECTARE, SOTKA, or SQM")
        String areaUnit,

        String boundaryGeojson,

        String notes
) {}
