package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.ShipmentRequestEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentRequestDetailDto(
        UUID id,
        String requestNumber,
        UUID requesterUserId,
        UUID orderId,
        UUID harvestBatchId,
        String cargoDescription,
        BigDecimal cargoWeightKg,
        BigDecimal cargoVolumeM3,
        String cargoType,
        String vehicleRequirements,
        String originRegion,
        String originDistrict,
        String originAddress,
        String originContactName,
        String originContactPhone,
        String destinationRegion,
        String destinationDistrict,
        String destinationAddress,
        String destinationContactName,
        String destinationContactPhone,
        Instant pickupWindowStart,
        Instant pickupWindowEnd,
        Instant deliveryDeadline,
        String specialInstructions,
        String status,
        UUID assignedCarrierUserId,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShipmentRequestDetailDto from(ShipmentRequestEntity entity, boolean canViewPrivateDetails) {
        String originAddress = canViewPrivateDetails ? entity.getOriginAddress() : "[HIDDEN_UNTIL_ASSIGNED]";
        String originPhone = canViewPrivateDetails ? entity.getOriginContactPhone() : "[HIDDEN_UNTIL_ASSIGNED]";
        String destinationAddress = canViewPrivateDetails ? entity.getDestinationAddress() : "[HIDDEN_UNTIL_ASSIGNED]";
        String destinationPhone = canViewPrivateDetails ? entity.getDestinationContactPhone() : "[HIDDEN_UNTIL_ASSIGNED]";

        return new ShipmentRequestDetailDto(
                entity.getId(),
                entity.getRequestNumber(),
                entity.getRequesterUserId(),
                entity.getOrderId(),
                entity.getHarvestBatchId(),
                entity.getCargoDescription(),
                entity.getCargoWeightKg(),
                entity.getCargoVolumeM3(),
                entity.getCargoType(),
                entity.getVehicleRequirements(),
                entity.getOriginRegion(),
                entity.getOriginDistrict(),
                originAddress,
                entity.getOriginContactName(),
                originPhone,
                entity.getDestinationRegion(),
                entity.getDestinationDistrict(),
                destinationAddress,
                entity.getDestinationContactName(),
                destinationPhone,
                entity.getPickupWindowStart(),
                entity.getPickupWindowEnd(),
                entity.getDeliveryDeadline(),
                entity.getSpecialInstructions(),
                entity.getStatus(),
                entity.getAssignedCarrierUserId(),
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
