package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateFarmRequest(
        @Size(min = 2, max = 150, message = "Farm name must be between 2 and 150 characters")
        String name,

        @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "totalAreaHa must be a valid positive decimal")
        String totalAreaHa,

        @Pattern(regexp = "^(DEHQON|FERMER|MCHJ|TOMORQA)$", message = "legalType must be one of DEHQON, FERMER, MCHJ, TOMORQA")
        String legalType
) {}
