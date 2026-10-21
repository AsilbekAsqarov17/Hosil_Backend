package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for commercial supplier creating an offer on a catalog product.
 */
public record CreateSellerOfferRequest(
        @NotNull(message = "Mahsulot tanlanishi shart")
        UUID catalogProductId,

        @NotNull(message = "Narx kiritilishi shart")
        @DecimalMin(value = "0.01", message = "Narx noldan katta boʻlishi kerak")
        BigDecimal unitPrice,

        String currency,

        @NotBlank(message = "Oʻlchov birligi kiritilishi shart")
        String unitCode,

        @NotNull(message = "Mavjud miqdor kiritilishi shart")
        @DecimalMin(value = "0.000", message = "Mavjud miqdor manfiy boʻlishi mumkin emas")
        BigDecimal availableQuantity,

        @DecimalMin(value = "0.001", message = "Minimal buyurtma miqdori noldan katta boʻlishi kerak")
        BigDecimal minOrderQuantity,

        @DecimalMin(value = "0.001", message = "Qadam miqdori noldan katta boʻlishi kerak")
        BigDecimal stepQuantity,

        String region,

        String district,

        String notes
) {}
