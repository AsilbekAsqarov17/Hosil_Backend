package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateCarrierVerificationRequest(
        @NotBlank(message = "Status is required")
        String status
) {}
