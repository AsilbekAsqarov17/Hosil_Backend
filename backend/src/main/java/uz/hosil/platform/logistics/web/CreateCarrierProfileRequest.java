package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.NotBlank;

public record CreateCarrierProfileRequest(
        @NotBlank(message = "Company name is required")
        String companyName,
        @NotBlank(message = "License number is required")
        String licenseNumber
) {}
