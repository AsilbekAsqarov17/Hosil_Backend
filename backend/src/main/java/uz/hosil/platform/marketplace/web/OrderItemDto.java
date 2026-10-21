package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.OrderItemEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO representing an order item line snapshot.
 */
public record OrderItemDto(
        UUID id,
        UUID harvestListingId,
        UUID sellerOfferId,
        UUID catalogProductId,
        String itemSnapshotTitle,
        String cropType,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitPrice,
        BigDecimal subtotalAmount,
        String currency
) {
    public static OrderItemDto fromEntity(OrderItemEntity entity) {
        return new OrderItemDto(
                entity.getId(),
                entity.getHarvestListingId(),
                entity.getSellerOfferId(),
                entity.getCatalogProductId(),
                entity.getItemSnapshotTitle(),
                entity.getCropType(),
                entity.getQuantity(),
                entity.getUnitCode(),
                entity.getUnitPrice(),
                entity.getSubtotalAmount(),
                entity.getCurrency()
        );
    }
}
