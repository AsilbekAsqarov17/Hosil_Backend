package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.ShipmentRequestEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentRequestSummaryDto(
        UUID id,
        String requestNumber,
        UUID orderId,
        UUID harvestBatchId,
        String cargoDescription,
        BigDecimal cargoWeightKg,
        String originRegion,
        String originDistrict,
        String destinationRegion,
        String destinationDistrict,
        Instant pickupWindowStart,
        Instant pickupWindowEnd,
        String status,
        UUID assignedCarrierUserId,
        Instant createdAt
) {
    public static ShipmentRequestSummaryDto from(ShipmentRequestEntity entity) {
        return new ShipmentRequestSummaryDto(
                entity.getId(),
                entity.getRequestNumber(),
                entity.getOrderId(),
                entity.getHarvestBatchId(),
                entity.getCargoDescription(),
                entity.getCargoWeightKg(),
                entity.getOriginRegion(),
                entity.getOriginDistrict(),
                entity.getDestinationRegion(),
                entity.getDestinationDistrict(),
                entity.getPickupWindowStart(),
                entity.getPickupWindowEnd(),
                entity.getStatus(),
                entity.getAssignedCarrierUserId(),
                entity.getCreatedAt()
        );
    }
}
