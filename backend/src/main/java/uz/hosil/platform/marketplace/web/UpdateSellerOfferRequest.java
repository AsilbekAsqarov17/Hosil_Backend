package uz.hosil.platform.marketplace.web;

import java.math.BigDecimal;

/**
 * Request payload for updating permitted commercial fields on a seller offer.
 */
public record UpdateSellerOfferRequest(
        BigDecimal unitPrice,
        String currency,
        String unitCode,
        BigDecimal availableQuantity,
        BigDecimal minOrderQuantity,
        BigDecimal stepQuantity,
        String region,
        String district,
        String notes,
        String status
) {}
