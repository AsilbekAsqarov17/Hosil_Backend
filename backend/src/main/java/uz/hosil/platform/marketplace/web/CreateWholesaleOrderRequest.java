package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for creating a wholesale purchase order from an agreed RFQ inquiry.
 */
public record CreateWholesaleOrderRequest(
        @NotNull(message = "inquiryId kiritilishi shart")
        UUID inquiryId,

        String deliveryType,

        String deliveryAddress,

        String notes
) {}
