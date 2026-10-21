package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for initiating a buyer RFQ inquiry on an active harvest listing.
 */
public record CreateInquiryRequest(
        @NotNull(message = "Miqdor kiritilishi shart")
        @DecimalMin(value = "0.001", message = "Miqdor noldan katta boʻlishi kerak")
        BigDecimal quantity,

        @NotBlank(message = "Oʻlchov birligi kiritilishi shart")
        String unitCode,

        BigDecimal unitPrice,

        String currency,

        String deliveryTerms,

        String notes,

        Instant expiresAt
) {}
