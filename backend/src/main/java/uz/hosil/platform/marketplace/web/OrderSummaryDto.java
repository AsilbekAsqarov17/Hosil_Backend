package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.OrderEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for paginated order lists (buyer or seller dashboard).
 */
public record OrderSummaryDto(
        UUID id,
        String orderNumber,
        UUID buyerUserId,
        UUID sellerUserId,
        UUID harvestListingId,
        String cropType,
        BigDecimal totalAmount,
        String currency,
        String status,
        String deliveryType,
        Instant confirmationExpiresAt,
        Instant createdAt
) {
    public static OrderSummaryDto fromEntity(OrderEntity order, String cropType) {
        return new OrderSummaryDto(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerUserId(),
                order.getSellerUserId(),
                order.getHarvestListingId(),
                cropType,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getDeliveryType(),
                order.getConfirmationExpiresAt(),
                order.getCreatedAt()
        );
    }
}
