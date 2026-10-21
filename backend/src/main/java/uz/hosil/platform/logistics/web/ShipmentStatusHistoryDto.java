package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.ShipmentStatusHistoryEntity;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusHistoryDto(
        UUID id,
        UUID shipmentRequestId,
        String fromStatus,
        String toStatus,
        UUID actorUserId,
        String actorRole,
        String notes,
        Instant createdAt
) {
    public static ShipmentStatusHistoryDto from(ShipmentStatusHistoryEntity entity) {
        return new ShipmentStatusHistoryDto(
                entity.getId(),
                entity.getShipmentRequestId(),
                entity.getFromStatus(),
                entity.getToStatus(),
                entity.getActorUserId(),
                entity.getActorRole(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }
}
