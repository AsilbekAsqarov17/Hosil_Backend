package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for submitting a counteroffer in an active RFQ inquiry.
 */
public record SubmitOfferRequest(
        @NotNull(message = "Miqdor kiritilishi shart")
        @DecimalMin(value = "0.001", message = "Miqdor noldan katta boʻlishi kerak")
        BigDecimal quantity,

        @NotNull(message = "Narx kiritilishi shart")
        @DecimalMin(value = "0.01", message = "Narx noldan katta boʻlishi kerak")
        BigDecimal unitPrice,

        String currency,

        String deliveryTerms,

        String notes,

        Instant expiresAt
) {}
