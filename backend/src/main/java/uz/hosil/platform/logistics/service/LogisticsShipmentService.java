package uz.hosil.platform.logistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.FarmHarvestBridge;
import uz.hosil.platform.farm.HarvestBatchSummary;
import uz.hosil.platform.identity.CarrierProfileInfo;
import uz.hosil.platform.identity.IdentityLookupService;
import uz.hosil.platform.logistics.domain.ShipmentRequestEntity;
import uz.hosil.platform.logistics.domain.ShipmentStatusHistoryEntity;
import uz.hosil.platform.logistics.repository.ShipmentRequestRepository;
import uz.hosil.platform.logistics.repository.ShipmentStatusHistoryRepository;
import uz.hosil.platform.logistics.web.CancelShipmentRequest;
import uz.hosil.platform.logistics.web.CarrierProfileDto;
import uz.hosil.platform.logistics.web.CreateCarrierProfileRequest;
import uz.hosil.platform.logistics.web.CreateShipmentRequest;
import uz.hosil.platform.logistics.web.PageEnvelope;
import uz.hosil.platform.logistics.web.ShipmentRequestDetailDto;
import uz.hosil.platform.logistics.web.ShipmentRequestSummaryDto;
import uz.hosil.platform.logistics.web.ShipmentStatusHistoryDto;
import uz.hosil.platform.logistics.web.UpdateShipmentStatusRequest;
import uz.hosil.platform.marketplace.LinkedOrderSummary;
import uz.hosil.platform.marketplace.MarketplaceOrderBridge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LogisticsShipmentService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsShipmentService.class);

    private static final Set<String> ACTIVE_SHIPMENT_STATUSES = Set.of(
            "REQUESTED", "QUOTED", "ACCEPTED", "PICKUP_ARRANGED", "PICKED_UP", "IN_TRANSIT"
    );

    private static final Set<String> ELIGIBLE_ORDER_STATUSES = Set.of(
            "CONFIRMED", "PENDING_PAYMENT", "PROCESSING", "READY_FOR_DISPATCH"
    );

    private final ShipmentRequestRepository shipmentRequestRepository;
    private final ShipmentStatusHistoryRepository shipmentStatusHistoryRepository;
    private final IdentityLookupService identityLookupService;
    private final MarketplaceOrderBridge marketplaceOrderBridge;
    private final FarmHarvestBridge farmHarvestBridge;

    public LogisticsShipmentService(
            ShipmentRequestRepository shipmentRequestRepository,
            ShipmentStatusHistoryRepository shipmentStatusHistoryRepository,
            IdentityLookupService identityLookupService,
            MarketplaceOrderBridge marketplaceOrderBridge,
            FarmHarvestBridge farmHarvestBridge
    ) {
        this.shipmentRequestRepository = shipmentRequestRepository;
        this.shipmentStatusHistoryRepository = shipmentStatusHistoryRepository;
        this.identityLookupService = identityLookupService;
        this.marketplaceOrderBridge = marketplaceOrderBridge;
        this.farmHarvestBridge = farmHarvestBridge;
    }

    private void validateShipmentIdempotentMatch(ShipmentRequestEntity existing, CreateShipmentRequest request) {
        if (!Objects.equals(existing.getOrderId(), request.orderId())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched orderId");
        }
        if (!Objects.equals(existing.getHarvestBatchId(), request.harvestBatchId())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched harvestBatchId");
        }
        if (request.cargoDescription() != null && !request.cargoDescription().isBlank()
                && !existing.getCargoDescription().equalsIgnoreCase(request.cargoDescription().trim())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched cargoDescription");
        }
        if (request.cargoWeightKg() != null
                && existing.getCargoWeightKg().compareTo(request.cargoWeightKg()) != 0) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched cargoWeightKg");
        }
        if (request.originRegion() != null && !request.originRegion().isBlank()
                && !existing.getOriginRegion().equalsIgnoreCase(request.originRegion().trim())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched originRegion");
        }
        if (request.originDistrict() != null && !request.originDistrict().isBlank()
                && !existing.getOriginDistrict().equalsIgnoreCase(request.originDistrict().trim())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched originDistrict");
        }
        if (request.destinationRegion() != null && !request.destinationRegion().isBlank()
                && !existing.getDestinationRegion().equalsIgnoreCase(request.destinationRegion().trim())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched destinationRegion");
        }
        if (request.destinationDistrict() != null && !request.destinationDistrict().isBlank()
                && !existing.getDestinationDistrict().equalsIgnoreCase(request.destinationDistrict().trim())) {
            throw new BusinessConflictException("Idempotency key reuse with mismatched destinationDistrict");
        }
    }

    @Transactional
    public ShipmentRequestDetailDto createShipmentRequest(CreateShipmentRequest request, UUID requesterUserId) {
        // 1. Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<ShipmentRequestEntity> existing = shipmentRequestRepository
                    .findByRequesterUserIdAndIdempotencyKey(requesterUserId, request.idempotencyKey());
            if (existing.isPresent()) {
                validateShipmentIdempotentMatch(existing.get(), request);
                log.info("Idempotent shipment request hit for key={}", request.idempotencyKey());
                return ShipmentRequestDetailDto.from(existing.get(), true);
            }
        }

        // 2. Validate pickup windows
        if (request.pickupWindowStart() == null || request.pickupWindowEnd() == null) {
            throw new BusinessConflictException("Pickup window start and end times are required");
        }
        if (request.pickupWindowEnd().isBefore(request.pickupWindowStart())) {
            throw new BusinessConflictException("Pickup window end cannot precede pickup window start");
        }
        if (request.deliveryDeadline() != null && request.deliveryDeadline().isBefore(request.pickupWindowStart())) {
            throw new BusinessConflictException("Delivery deadline cannot precede pickup window start");
        }

        String cargoDesc = request.cargoDescription();
        BigDecimal cargoWeight = request.cargoWeightKg();
        String originReg = request.originRegion();
        String originDist = request.originDistrict();
        String originAddr = request.originAddress();
        String destReg = request.destinationRegion();
        String destDist = request.destinationDistrict();
        String destAddr = request.destinationAddress();

        LinkedOrderSummary linkedOrder = null;
        HarvestBatchSummary linkedBatch = null;

        // 3. Linked order verification
        if (request.orderId() != null) {
            linkedOrder = marketplaceOrderBridge.findOrderForTransport(request.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked order not found: " + request.orderId()));

            // Authorization: Requester must be buyer or seller of the order
            if (!linkedOrder.buyerUserId().equals(requesterUserId) && !linkedOrder.sellerUserId().equals(requesterUserId)) {
                throw new AccessDeniedException("Only the order buyer or seller can request shipment for order " + linkedOrder.orderNumber());
            }

            // Status check: Order must be in eligible state
            if (!ELIGIBLE_ORDER_STATUSES.contains(linkedOrder.status())) {
                throw new BusinessConflictException("Order is in status " + linkedOrder.status() + " and not eligible for transport request");
            }

            // Active request check on order
            List<ShipmentRequestEntity> activeOnOrder = shipmentRequestRepository
                    .findByOrderIdAndStatusIn(linkedOrder.orderId(), ACTIVE_SHIPMENT_STATUSES);
            if (!activeOnOrder.isEmpty()) {
                throw new BusinessConflictException("An active shipment request already exists for order " + linkedOrder.orderNumber());
            }

            // Defaults from order
            if (cargoDesc == null || cargoDesc.isBlank()) {
                cargoDesc = linkedOrder.cargoDescription();
            }
            if (destAddr == null || destAddr.isBlank()) {
                destAddr = linkedOrder.deliveryAddress();
            }
        }

        // 4. Linked harvest batch verification
        if (request.harvestBatchId() != null) {
            linkedBatch = farmHarvestBridge.findHarvestBatchSummary(request.harvestBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked harvest batch not found: " + request.harvestBatchId()));

            // Standalone harvest batch verification and active request guard (when not linked to an order)
            if (request.orderId() == null) {
                if (!linkedBatch.farmOwnerUserId().equals(requesterUserId)) {
                    throw new AccessDeniedException("Only the owning farm member can request transport for harvest batch " + linkedBatch.batchId());
                }

                List<ShipmentRequestEntity> activeOnBatch = shipmentRequestRepository
                        .findByHarvestBatchIdAndOrderIdIsNullAndStatusIn(linkedBatch.batchId(), ACTIVE_SHIPMENT_STATUSES);
                if (!activeOnBatch.isEmpty()) {
                    throw new BusinessConflictException("An active shipment request already exists for harvest batch " + linkedBatch.batchId());
                }
            }

            if (cargoDesc == null || cargoDesc.isBlank()) {
                cargoDesc = linkedBatch.cropType() + " (" + linkedBatch.unitCode() + ")";
            }
            if (originReg == null || originReg.isBlank()) {
                originReg = linkedBatch.region();
            }
            if (originDist == null || originDist.isBlank()) {
                originDist = linkedBatch.district();
            }
        }

        // 5. Cross-linkage consistency check: When BOTH order and harvest batch are supplied
        if (linkedOrder != null && linkedBatch != null) {
            if (linkedOrder.harvestBatchId() == null) {
                throw new BusinessConflictException("Order " + linkedOrder.orderNumber() + " is not a wholesale harvest order and cannot be linked with a harvest batch");
            }
            if (!linkedOrder.harvestBatchId().equals(linkedBatch.batchId())) {
                throw new BusinessConflictException("Linked harvest batch " + linkedBatch.batchId() + " does not match order backing batch " + linkedOrder.harvestBatchId());
            }
        }

        // 6. Weight derivation & validation
        if (cargoWeight == null || cargoWeight.compareTo(BigDecimal.ZERO) <= 0) {
            if (linkedOrder != null) {
                if (linkedOrder.hasMixedUnits()) {
                    throw new BusinessConflictException("Order contains items with mixed units. Explicit cargoWeightKg must be provided.");
                } else if (linkedOrder.derivedWeightKg() != null && linkedOrder.derivedWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                    cargoWeight = linkedOrder.derivedWeightKg();
                } else {
                    throw new BusinessConflictException("Cargo weight in kg cannot be derived automatically from order units (" + linkedOrder.unitCode() + "). Explicit cargoWeightKg must be provided.");
                }
            } else if (linkedBatch != null) {
                String unit = linkedBatch.unitCode() != null ? linkedBatch.unitCode().trim().toUpperCase(Locale.ROOT) : "";
                if ("KG".equals(unit)) {
                    cargoWeight = linkedBatch.unallocatedQuantity();
                } else if ("TON".equals(unit) || "T".equals(unit)) {
                    cargoWeight = linkedBatch.unallocatedQuantity().multiply(BigDecimal.valueOf(1000));
                } else {
                    throw new BusinessConflictException("Cargo weight in kg cannot be derived automatically from harvest batch unit (" + linkedBatch.unitCode() + "). Explicit cargoWeightKg must be provided.");
                }
            } else {
                throw new BusinessConflictException("Cargo weight must be greater than zero for standalone shipment requests");
            }
        }

        // 7. Final required field validations
        if (cargoDesc == null || cargoDesc.isBlank()) {
            throw new BusinessConflictException("Cargo description is required");
        }
        if (cargoWeight == null || cargoWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Cargo weight must be greater than zero");
        }
        if (originReg == null || originReg.isBlank() || originDist == null || originDist.isBlank()) {
            throw new BusinessConflictException("Origin region and district are required");
        }
        if (destReg == null || destReg.isBlank() || destDist == null || destDist.isBlank()) {
            throw new BusinessConflictException("Destination region and district are required");
        }

        // 8. Generate unique readable request number
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String requestNumber = "SR-" + datePart + "-" + randomSuffix;

        UUID effectiveHarvestBatchId = request.harvestBatchId() != null
                ? request.harvestBatchId()
                : (linkedOrder != null ? linkedOrder.harvestBatchId() : null);

        ShipmentRequestEntity entity = new ShipmentRequestEntity(
                UUID.randomUUID(),
                requestNumber,
                requesterUserId,
                request.orderId(),
                effectiveHarvestBatchId,
                cargoDesc,
                cargoWeight,
                request.cargoVolumeM3(),
                request.cargoType(),
                request.vehicleRequirements(),
                originReg,
                originDist,
                originAddr,
                request.originContactName(),
                request.originContactPhone(),
                destReg,
                destDist,
                destAddr,
                request.destinationContactName(),
                request.destinationContactPhone(),
                request.pickupWindowStart(),
                request.pickupWindowEnd(),
                request.deliveryDeadline(),
                request.specialInstructions(),
                "REQUESTED",
                null,
                request.idempotencyKey()
        );

        try {
            entity = shipmentRequestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException dive) {
            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                Optional<ShipmentRequestEntity> concurrent = shipmentRequestRepository
                        .findByRequesterUserIdAndIdempotencyKey(requesterUserId, request.idempotencyKey());
                if (concurrent.isPresent()) {
                    validateShipmentIdempotentMatch(concurrent.get(), request);
                    return ShipmentRequestDetailDto.from(concurrent.get(), true);
                }
            }
            throw new BusinessConflictException("An active shipment request already exists or database constraint violation: " + dive.getMessage());
        }

        // Record creation history
        ShipmentStatusHistoryEntity history = new ShipmentStatusHistoryEntity(
                UUID.randomUUID(),
                entity.getId(),
                null,
                "REQUESTED",
                requesterUserId,
                "REQUESTER",
                "Shipment request submitted"
        );
        shipmentStatusHistoryRepository.save(history);

        log.info("Shipment request {} created by user {}", entity.getRequestNumber(), requesterUserId);
        return ShipmentRequestDetailDto.from(entity, true);
    }

    @Transactional(readOnly = true)
    public ShipmentRequestDetailDto getShipmentRequest(UUID requestId, UUID callerUserId, boolean isAdmin) {
        ShipmentRequestEntity entity = shipmentRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment request not found: " + requestId));

        boolean isRequester = entity.getRequesterUserId().equals(callerUserId);
        boolean isAssignedCarrier = entity.getAssignedCarrierUserId() != null && entity.getAssignedCarrierUserId().equals(callerUserId);

        // Privacy: Unmasked strictly for requester, assigned carrier, or admin
        boolean canViewPrivateDetails = isRequester || isAssignedCarrier || isAdmin;

        // If caller is an outsider and status is not open, forbid access
        if (!canViewPrivateDetails && !("REQUESTED".equals(entity.getStatus()) || "QUOTED".equals(entity.getStatus()))) {
            throw new AccessDeniedException("Access denied to shipment request details");
        }

        return ShipmentRequestDetailDto.from(entity, canViewPrivateDetails);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<ShipmentRequestSummaryDto> listShipmentRequests(
            String status,
            String originRegion,
            String destinationRegion,
            Boolean mineOnly,
            Boolean assignedToMe,
            UUID callerUserId,
            Pageable pageable
    ) {
        Specification<ShipmentRequestEntity> spec = Specification.where(null);

        if (Boolean.TRUE.equals(mineOnly)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("requesterUserId"), callerUserId));
        } else if (Boolean.TRUE.equals(assignedToMe)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedCarrierUserId"), callerUserId));
        } else if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status.toUpperCase(Locale.ROOT)));
        } else {
            // Default discovery board: open requests
            spec = spec.and((root, query, cb) -> root.get("status").in("REQUESTED", "QUOTED"));
        }

        if (originRegion != null && !originRegion.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("originRegion")), originRegion.toLowerCase(Locale.ROOT)));
        }
        if (destinationRegion != null && !destinationRegion.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("destinationRegion")), destinationRegion.toLowerCase(Locale.ROOT)));
        }

        Page<ShipmentRequestEntity> page = shipmentRequestRepository.findAll(spec, pageable);
        return PageEnvelope.from(page.map(ShipmentRequestSummaryDto::from));
    }

    @Transactional
    public ShipmentRequestDetailDto acceptShipmentRequest(UUID requestId, UUID carrierUserId) {
        // 1. Verify caller has carrier profile and is verified (DEC-011)
        CarrierProfileInfo carrierProfile = identityLookupService.findCarrierProfile(carrierUserId)
                .orElseThrow(() -> new BusinessConflictException("Carrier must register a carrier profile before accepting shipment requests"));

        if (!"VERIFIED".equalsIgnoreCase(carrierProfile.verificationStatus())) {
            throw new BusinessConflictException("Carrier profile verification status is " + carrierProfile.verificationStatus() + "; only VERIFIED carriers can accept shipments");
        }

        // 2. Lock shipment request with pessimistic write
        ShipmentRequestEntity entity = shipmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment request not found: " + requestId));

        // 3. Status check: must be REQUESTED or QUOTED
        if (!"REQUESTED".equals(entity.getStatus()) && !"QUOTED".equals(entity.getStatus())) {
            throw new BusinessConflictException("Shipment request is in status " + entity.getStatus() + " and cannot be accepted");
        }

        if (entity.getAssignedCarrierUserId() != null) {
            throw new BusinessConflictException("Shipment request is already assigned to another carrier");
        }

        String oldStatus = entity.getStatus();
        entity.setStatus("ACCEPTED");
        entity.setAssignedCarrierUserId(carrierUserId);
        entity = shipmentRequestRepository.save(entity);

        // Record history
        ShipmentStatusHistoryEntity history = new ShipmentStatusHistoryEntity(
                UUID.randomUUID(),
                entity.getId(),
                oldStatus,
                "ACCEPTED",
                carrierUserId,
                "CARRIER",
                "Accepted by carrier " + carrierProfile.companyName()
        );
        shipmentStatusHistoryRepository.save(history);

        log.info("Shipment request {} accepted by carrier {}", entity.getRequestNumber(), carrierUserId);
        return ShipmentRequestDetailDto.from(entity, true);
    }

    @Transactional
    public ShipmentRequestDetailDto updateShipmentStatus(
            UUID requestId,
            UpdateShipmentStatusRequest request,
            UUID callerUserId,
            boolean isAdmin
    ) {
        ShipmentRequestEntity entity = shipmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment request not found: " + requestId));

        boolean isAssignedCarrier = entity.getAssignedCarrierUserId() != null && entity.getAssignedCarrierUserId().equals(callerUserId);
        if (!isAssignedCarrier && !isAdmin) {
            throw new AccessDeniedException("Only the assigned carrier or administrator can update shipment status");
        }

        String currentStatus = entity.getStatus();
        String targetStatus = request.targetStatus().toUpperCase(Locale.ROOT);

        // Terminal states cannot transition
        if ("DELIVERED".equals(currentStatus) || "CANCELLED".equals(currentStatus) || "EXPIRED".equals(currentStatus)) {
            throw new BusinessConflictException("Cannot update shipment status from terminal status " + currentStatus);
        }

        // State Machine forward progression validation
        boolean validTransition = switch (currentStatus) {
            case "ACCEPTED" -> "PICKUP_ARRANGED".equals(targetStatus);
            case "PICKUP_ARRANGED" -> "PICKED_UP".equals(targetStatus);
            case "PICKED_UP" -> "IN_TRANSIT".equals(targetStatus);
            case "IN_TRANSIT" -> "DELIVERED".equals(targetStatus);
            default -> false;
        };

        if (!validTransition) {
            throw new BusinessConflictException("Invalid shipment status transition from " + currentStatus + " to " + targetStatus);
        }

        // Physical dispatch authorization safety gate (Milestone 6B.12.2):
        // Physical dispatch (PICKED_UP) irreversibly transfers custody of produce/goods.
        // For shipments associated with marketplace orders, seller confirmation (CONFIRMED) does NOT
        // constitute verified financial clearance. Because DEC-007 (payment settlement) remains unresolved
        // and no authoritative payment-verification or dispatch-authorization mechanism exists,
        // physical dispatch transitions for order-linked shipments must fail closed.
        if ("PICKED_UP".equals(targetStatus) && entity.getOrderId() != null) {
            LinkedOrderSummary order = marketplaceOrderBridge.findOrderForTransport(entity.getOrderId()).orElse(null);
            if (order != null) {
                if ("CANCELLED".equalsIgnoreCase(order.status())) {
                    throw new BusinessConflictException("Cannot dispatch cargo (PICKED_UP) for cancelled order " + order.orderNumber());
                }
                if ("PENDING_PAYMENT".equalsIgnoreCase(order.status())) {
                    throw new BusinessConflictException("Cannot dispatch cargo (PICKED_UP) for order " + order.orderNumber()
                            + " with payment pending. Order must be financially cleared and authorized for dispatch before pickup.");
                }
                throw new BusinessConflictException("Cannot dispatch cargo (PICKED_UP) for order " + order.orderNumber()
                        + ": order is in status " + order.status() + " which does not constitute verified financial clearance. "
                        + "Physical dispatch authorization is blocked pending resolution of payment settlement (DEC-007).");
            } else {
                throw new BusinessConflictException("Cannot dispatch cargo for non-existent order " + entity.getOrderId());
            }
        }

        entity.setStatus(targetStatus);
        entity = shipmentRequestRepository.save(entity);

        ShipmentStatusHistoryEntity history = new ShipmentStatusHistoryEntity(
                UUID.randomUUID(),
                entity.getId(),
                currentStatus,
                targetStatus,
                callerUserId,
                isAdmin ? "ADMINISTRATOR" : "CARRIER",
                request.notes() != null ? request.notes() : "Status updated to " + targetStatus
        );
        shipmentStatusHistoryRepository.save(history);

        log.info("Shipment request {} transitioned from {} to {}", entity.getRequestNumber(), currentStatus, targetStatus);
        return ShipmentRequestDetailDto.from(entity, true);
    }

    @Transactional
    public ShipmentRequestDetailDto cancelShipmentRequest(
            UUID requestId,
            CancelShipmentRequest request,
            UUID callerUserId,
            boolean isAdmin
    ) {
        ShipmentRequestEntity entity = shipmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment request not found: " + requestId));

        boolean isRequester = entity.getRequesterUserId().equals(callerUserId);
        if (!isRequester && !isAdmin) {
            throw new AccessDeniedException("Only the requester or administrator can cancel this shipment request");
        }

        // Permitted only prior to cargo pickup
        Set<String> cancellableStatuses = Set.of("REQUESTED", "QUOTED", "ACCEPTED", "PICKUP_ARRANGED");
        if (!cancellableStatuses.contains(entity.getStatus())) {
            throw new BusinessConflictException("Cannot cancel shipment request in status " + entity.getStatus() + ". Cargo is already loaded/in-transit or terminal.");
        }

        String oldStatus = entity.getStatus();
        entity.setStatus("CANCELLED");
        entity.setCancellationReason(request.reason());
        entity = shipmentRequestRepository.save(entity);

        ShipmentStatusHistoryEntity history = new ShipmentStatusHistoryEntity(
                UUID.randomUUID(),
                entity.getId(),
                oldStatus,
                "CANCELLED",
                callerUserId,
                isAdmin ? "ADMINISTRATOR" : "REQUESTER",
                "Cancelled: " + request.reason()
        );
        shipmentStatusHistoryRepository.save(history);

        log.info("Shipment request {} cancelled by user {} from status {}", entity.getRequestNumber(), callerUserId, oldStatus);
        return ShipmentRequestDetailDto.from(entity, true);
    }

    @Transactional(readOnly = true)
    public List<ShipmentStatusHistoryDto> getShipmentHistory(UUID requestId, UUID callerUserId, boolean isAdmin) {
        ShipmentRequestEntity entity = shipmentRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment request not found: " + requestId));

        boolean isRequester = entity.getRequesterUserId().equals(callerUserId);
        boolean isAssignedCarrier = entity.getAssignedCarrierUserId() != null && entity.getAssignedCarrierUserId().equals(callerUserId);

        if (!isRequester && !isAssignedCarrier && !isAdmin) {
            throw new AccessDeniedException("Access denied to shipment status history");
        }

        return shipmentStatusHistoryRepository.findByShipmentRequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(ShipmentStatusHistoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CarrierProfileDto getCarrierProfile(UUID userId) {
        CarrierProfileInfo info = identityLookupService.findCarrierProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found for user: " + userId));
        return CarrierProfileDto.from(info);
    }

    @Transactional
    public CarrierProfileDto upsertCarrierProfile(UUID userId, CreateCarrierProfileRequest request) {
        CarrierProfileInfo info = identityLookupService.registerOrUpdateCarrierProfile(
                userId,
                request.companyName(),
                request.licenseNumber()
        );
        return CarrierProfileDto.from(info);
    }

    @Transactional
    public CarrierProfileDto adminUpdateCarrierVerification(UUID carrierUserId, String newStatus) {
        CarrierProfileInfo info = identityLookupService.adminSetCarrierVerificationStatus(carrierUserId, newStatus);
        return CarrierProfileDto.from(info);
    }
}
