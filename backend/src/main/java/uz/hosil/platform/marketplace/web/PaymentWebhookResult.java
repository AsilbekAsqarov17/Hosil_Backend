package uz.hosil.platform.marketplace.web;

import java.util.UUID;

/**
 * Result returned to webhook caller or simulation test.
 */
public record PaymentWebhookResult(
        boolean success,
        UUID paymentId,
        String status,
        String message
) {}
