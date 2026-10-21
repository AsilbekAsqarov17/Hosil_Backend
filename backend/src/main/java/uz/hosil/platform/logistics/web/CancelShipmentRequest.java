package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.NotBlank;

public record CancelShipmentRequest(
        @NotBlank(message = "Cancellation reason is required")
        String reason
) {}
