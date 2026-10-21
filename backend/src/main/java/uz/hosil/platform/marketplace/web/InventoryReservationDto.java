package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an inventory reservation record.
 */
public record InventoryReservationDto(
        UUID id,
        UUID orderId,
        UUID harvestListingId,
        UUID sellerOfferId,
        BigDecimal reservedQuantity,
        String unitCode,
        Instant expiresAt,
        String status,
        Instant createdAt
) {
    public static InventoryReservationDto fromEntity(InventoryReservationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new InventoryReservationDto(
                entity.getId(),
                entity.getOrderId(),
                entity.getHarvestListingId(),
                entity.getSellerOfferId(),
                entity.getReservedQuantity(),
                entity.getUnitCode(),
                entity.getExpiresAt(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
