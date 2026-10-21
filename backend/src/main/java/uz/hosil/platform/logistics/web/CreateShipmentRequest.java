package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateShipmentRequest(
        UUID orderId,
        UUID harvestBatchId,
        String cargoDescription,
        @DecimalMin(value = "0.01", message = "Cargo weight must be greater than zero")
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
        @NotNull(message = "Pickup window start is required")
        Instant pickupWindowStart,
        @NotNull(message = "Pickup window end is required")
        Instant pickupWindowEnd,
        Instant deliveryDeadline,
        String specialInstructions,
        String idempotencyKey
) {}
