package uz.hosil.platform.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Logistics shipment request entity representing cargo transport jobs (Table 28, LOG-001 - LOG-009).
 */
@Entity
@Table(name = "shipment_requests")
public class ShipmentRequestEntity {

    @Id
    private UUID id;

    @Column(name = "request_number", nullable = false, unique = true, length = 50)
    private String requestNumber;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "harvest_batch_id")
    private UUID harvestBatchId;

    @Column(name = "cargo_description", nullable = false)
    private String cargoDescription;

    @Column(name = "cargo_weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal cargoWeightKg;

    @Column(name = "cargo_volume_m3", precision = 10, scale = 2)
    private BigDecimal cargoVolumeM3;

    @Column(name = "cargo_type", length = 100)
    private String cargoType;

    @Column(name = "vehicle_requirements", length = 100)
    private String vehicleRequirements;

    @Column(name = "origin_region", nullable = false, length = 100)
    private String originRegion;

    @Column(name = "origin_district", nullable = false, length = 100)
    private String originDistrict;

    @Column(name = "origin_address")
    private String originAddress;

    @Column(name = "origin_contact_name", length = 150)
    private String originContactName;

    @Column(name = "origin_contact_phone", length = 50)
    private String originContactPhone;

    @Column(name = "destination_region", nullable = false, length = 100)
    private String destinationRegion;

    @Column(name = "destination_district", nullable = false, length = 100)
    private String destinationDistrict;

    @Column(name = "destination_address")
    private String destinationAddress;

    @Column(name = "destination_contact_name", length = 150)
    private String destinationContactName;

    @Column(name = "destination_contact_phone", length = 50)
    private String destinationContactPhone;

    @Column(name = "pickup_window_start", nullable = false)
    private Instant pickupWindowStart;

    @Column(name = "pickup_window_end", nullable = false)
    private Instant pickupWindowEnd;

    @Column(name = "delivery_deadline")
    private Instant deliveryDeadline;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "assigned_carrier_user_id")
    private UUID assignedCarrierUserId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ShipmentRequestEntity() {}

    public ShipmentRequestEntity(
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
            String idempotencyKey
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.requestNumber = requestNumber;
        this.requesterUserId = requesterUserId;
        this.orderId = orderId;
        this.harvestBatchId = harvestBatchId;
        this.cargoDescription = cargoDescription;
        this.cargoWeightKg = cargoWeightKg;
        this.cargoVolumeM3 = cargoVolumeM3;
        this.cargoType = cargoType;
        this.vehicleRequirements = vehicleRequirements;
        this.originRegion = originRegion;
        this.originDistrict = originDistrict;
        this.originAddress = originAddress;
        this.originContactName = originContactName;
        this.originContactPhone = originContactPhone;
        this.destinationRegion = destinationRegion;
        this.destinationDistrict = destinationDistrict;
        this.destinationAddress = destinationAddress;
        this.destinationContactName = destinationContactName;
        this.destinationContactPhone = destinationContactPhone;
        this.pickupWindowStart = pickupWindowStart;
        this.pickupWindowEnd = pickupWindowEnd;
        this.deliveryDeadline = deliveryDeadline;
        this.specialInstructions = specialInstructions;
        this.status = status != null ? status : "REQUESTED";
        this.assignedCarrierUserId = assignedCarrierUserId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRequestNumber() { return requestNumber; }
    public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }

    public UUID getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(UUID requesterUserId) { this.requesterUserId = requesterUserId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getHarvestBatchId() { return harvestBatchId; }
    public void setHarvestBatchId(UUID harvestBatchId) { this.harvestBatchId = harvestBatchId; }

    public String getCargoDescription() { return cargoDescription; }
    public void setCargoDescription(String cargoDescription) { this.cargoDescription = cargoDescription; }

    public BigDecimal getCargoWeightKg() { return cargoWeightKg; }
    public void setCargoWeightKg(BigDecimal cargoWeightKg) { this.cargoWeightKg = cargoWeightKg; }

    public BigDecimal getCargoVolumeM3() { return cargoVolumeM3; }
    public void setCargoVolumeM3(BigDecimal cargoVolumeM3) { this.cargoVolumeM3 = cargoVolumeM3; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public String getVehicleRequirements() { return vehicleRequirements; }
    public void setVehicleRequirements(String vehicleRequirements) { this.vehicleRequirements = vehicleRequirements; }

    public String getOriginRegion() { return originRegion; }
    public void setOriginRegion(String originRegion) { this.originRegion = originRegion; }

    public String getOriginDistrict() { return originDistrict; }
    public void setOriginDistrict(String originDistrict) { this.originDistrict = originDistrict; }

    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }

    public String getOriginContactName() { return originContactName; }
    public void setOriginContactName(String originContactName) { this.originContactName = originContactName; }

    public String getOriginContactPhone() { return originContactPhone; }
    public void setOriginContactPhone(String originContactPhone) { this.originContactPhone = originContactPhone; }

    public String getDestinationRegion() { return destinationRegion; }
    public void setDestinationRegion(String destinationRegion) { this.destinationRegion = destinationRegion; }

    public String getDestinationDistrict() { return destinationDistrict; }
    public void setDestinationDistrict(String destinationDistrict) { this.destinationDistrict = destinationDistrict; }

    public String getDestinationAddress() { return destinationAddress; }
    public void setDestinationAddress(String destinationAddress) { this.destinationAddress = destinationAddress; }

    public String getDestinationContactName() { return destinationContactName; }
    public void setDestinationContactName(String destinationContactName) { this.destinationContactName = destinationContactName; }

    public String getDestinationContactPhone() { return destinationContactPhone; }
    public void setDestinationContactPhone(String destinationContactPhone) { this.destinationContactPhone = destinationContactPhone; }

    public Instant getPickupWindowStart() { return pickupWindowStart; }
    public void setPickupWindowStart(Instant pickupWindowStart) { this.pickupWindowStart = pickupWindowStart; }

    public Instant getPickupWindowEnd() { return pickupWindowEnd; }
    public void setPickupWindowEnd(Instant pickupWindowEnd) { this.pickupWindowEnd = pickupWindowEnd; }

    public Instant getDeliveryDeadline() { return deliveryDeadline; }
    public void setDeliveryDeadline(Instant deliveryDeadline) { this.deliveryDeadline = deliveryDeadline; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getAssignedCarrierUserId() { return assignedCarrierUserId; }
    public void setAssignedCarrierUserId(UUID assignedCarrierUserId) { this.assignedCarrierUserId = assignedCarrierUserId; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
