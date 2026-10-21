package uz.hosil.platform.marketplace.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for creating an agricultural-input order from one or more seller offers (DEC-005).
 */
public record CreateInputOrderRequest(
        @NotEmpty(message = "Buyurtmada kamida bitta mahsulot boʻlishi kerak")
        List<@Valid InputOrderItemRequest> items,

        String deliveryType,

        String deliveryAddress,

        String deliveryTerms,

        String notes
) {
    public record InputOrderItemRequest(
            @NotNull(message = "sellerOfferId kiritilishi shart")
            UUID sellerOfferId,

            @NotNull(message = "quantity kiritilishi shart")
            @Positive(message = "quantity musbat boʻlishi kerak")
            BigDecimal quantity,

            @NotNull(message = "unitCode kiritilishi shart")
            String unitCode
    ) {}
}
