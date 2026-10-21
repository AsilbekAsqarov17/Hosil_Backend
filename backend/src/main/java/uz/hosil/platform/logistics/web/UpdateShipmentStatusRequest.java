package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateShipmentStatusRequest(
        @NotBlank(message = "Target status is required")
        String targetStatus,
        String notes
) {}
