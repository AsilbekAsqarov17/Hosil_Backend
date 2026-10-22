package uz.hosil.platform.marketplace.web;

import java.util.UUID;

/**
 * Request payload for the sandbox mock payment capture simulation endpoint.
 */
public record SimulatePaymentCaptureRequest(
        UUID paymentId,
        String paymentIntentReference,
        Boolean success,
        String failureReason
) {
    public SimulatePaymentCaptureRequest {
        if (success == null) {
            success = true;
        }
    }
}
