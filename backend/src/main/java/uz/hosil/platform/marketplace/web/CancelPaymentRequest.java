package uz.hosil.platform.marketplace.web;

/**
 * Request payload for payment cancellation.
 */
public record CancelPaymentRequest(
        String reason
) {
}
