package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload to initiate a payment attempt for a payable order (PAY-001).
 */
public record InitiatePaymentRequest(
        @NotNull(message = "Buyurtma identifikatori (orderId) majburiy")
        UUID orderId,
        String providerName
) {
    public InitiatePaymentRequest {
        if (providerName == null || providerName.isBlank()) {
            providerName = "MOCK_SANDBOX";
        }
    }
}
