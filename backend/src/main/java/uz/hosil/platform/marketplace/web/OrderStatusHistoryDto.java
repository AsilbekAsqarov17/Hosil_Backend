package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an order status transition audit record.
 */
public record OrderStatusHistoryDto(
        UUID id,
        String fromStatus,
        String toStatus,
        UUID actorUserId,
        String actorRole,
        String reason,
        Instant createdAt
) {
    public static OrderStatusHistoryDto fromEntity(OrderStatusHistoryEntity entity) {
        return new OrderStatusHistoryDto(
                entity.getId(),
                entity.getFromStatus(),
                entity.getToStatus(),
                entity.getActorUserId(),
                entity.getActorRole(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }
}
