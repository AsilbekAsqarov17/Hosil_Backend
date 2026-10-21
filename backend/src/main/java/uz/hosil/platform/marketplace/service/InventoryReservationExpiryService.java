package uz.hosil.platform.marketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uz.hosil.platform.farm.FarmHarvestBridge;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing transactional evaluation, deterministic locking,
 * and stock restoration for expired inventory reservations (DEC-006, DEC-010).
 */
@Service
public class InventoryReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationExpiryService.class);

    private final InventoryReservationRepository reservationRepository;
    private final HarvestListingRepository listingRepository;
    private final SellerOfferRepository sellerOfferRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final FarmHarvestBridge farmHarvestBridge;
    private final TransactionTemplate requiresNewTxTemplate;

    public InventoryReservationExpiryService(
            InventoryReservationRepository reservationRepository,
            HarvestListingRepository listingRepository,
            SellerOfferRepository sellerOfferRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            FarmHarvestBridge farmHarvestBridge,
            PlatformTransactionManager transactionManager
    ) {
        this.reservationRepository = reservationRepository;
        this.listingRepository = listingRepository;
        this.sellerOfferRepository = sellerOfferRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.farmHarvestBridge = farmHarvestBridge;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Finds eligible expired active reservations in bounded batches and processes them.
     *
     * @param batchSize maximum number of reservations to evaluate in this sweep run
     * @return count of reservations successfully transitioned to RELEASED_EXPIRED
     */
    public int sweepExpiredReservations(int batchSize) {
        Instant now = Instant.now();
        List<UUID> candidateIds = reservationRepository.findExpiredActiveReservationIds(
                now,
                org.springframework.data.domain.PageRequest.of(0, batchSize)
        );

        if (candidateIds.isEmpty()) {
            log.trace("No active expired inventory reservations found at {}", now);
            return 0;
        }

        log.info("Discovered {} candidate expired reservations for processing", candidateIds.size());
        int processedCount = 0;

        for (UUID reservationId : candidateIds) {
            try {
                boolean released = processSingleExpiredReservation(reservationId, now);
                if (released) {
                    processedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to process expired reservation: id={}", reservationId, ex);
            }
        }

        log.info("Completed expired inventory reservation sweep: releasedCount={}/{}", processedCount, candidateIds.size());
        return processedCount;
    }

    /**
     * Processes a single expired reservation inside an isolated transaction following
     * the strict deterministic lock acquisition hierarchy:
     * 1. harvest_batches (ordered by id)
     * 2. harvest_listings (ordered by id)
     * 3. inventory_reservations (atomic CAS transition)
     * 4. orders
     *
     * @param reservationId identifier of the reservation to evaluate
     * @param referenceTime authoritative threshold time against which expiration is evaluated
     * @return true if the reservation was expired and stock restored, false if skipped
     */
    public boolean processSingleExpiredReservation(UUID reservationId, Instant referenceTime) {
        Boolean result = requiresNewTxTemplate.execute(txStatus -> {
            // Step 1: Pre-check state without holding heavyweight locks
            Optional<InventoryReservationEntity> preCheckOpt = reservationRepository.findById(reservationId);
            if (preCheckOpt.isEmpty()) {
                log.debug("Reservation {} not found during pre-check; skipping", reservationId);
                return false;
            }

            InventoryReservationEntity initialRes = preCheckOpt.get();
            if (!"ACTIVE".equals(initialRes.getStatus())) {
                log.debug("Reservation {} is no longer ACTIVE (status={}); skipping", reservationId, initialRes.getStatus());
                return false;
            }

            if (initialRes.getExpiresAt().isAfter(referenceTime)) {
                log.debug("Reservation {} expiresAt {} is in future relative to {}; skipping",
                        reservationId, initialRes.getExpiresAt(), referenceTime);
                return false;
            }

            BigDecimal qtyToRestore = initialRes.getReservedQuantity();

            if (initialRes.getSellerOfferId() != null) {
                // Target: Agricultural-Input Seller Offer
                SellerOfferEntity offer = sellerOfferRepository.findByIdForUpdate(initialRes.getSellerOfferId())
                        .orElseThrow(() -> new IllegalStateException("Seller offer not found under lock: " + initialRes.getSellerOfferId()));

                OrderEntity order = null;
                if (initialRes.getOrderId() != null) {
                    order = orderRepository.findByIdForUpdate(initialRes.getOrderId()).orElse(null);
                }

                if (order != null) {
                    String orderStatus = order.getStatus();
                    if ("CONFIRMED".equals(orderStatus) || "PROCESSING".equals(orderStatus)
                            || "READY_FOR_DISPATCH".equals(orderStatus) || "IN_TRANSIT".equals(orderStatus)
                            || "COMPLETED".equals(orderStatus) || "DISPUTED".equals(orderStatus)) {
                        log.info("Reservation {} belongs to order {} in binding status {}; skipping expiration release",
                                reservationId, order.getId(), orderStatus);
                        return false;
                    }
                }

                int updatedRows = reservationRepository.transitionStatusIfActive(reservationId, "RELEASED_EXPIRED");
                if (updatedRows == 0) {
                    log.info("Reservation {} is no longer ACTIVE or was already released concurrently; skipping", reservationId);
                    return false;
                }

                if (offer.getReservedQuantity().compareTo(qtyToRestore) < 0) {
                    throw new IllegalStateException(String.format(
                            "Seller offer %s reservedQuantity (%s) is less than reservation quantity (%s); aborting to prevent negative balance",
                            offer.getId(), offer.getReservedQuantity(), qtyToRestore));
                }

                offer.setReservedQuantity(offer.getReservedQuantity().subtract(qtyToRestore));
                offer.setAvailableQuantity(offer.getAvailableQuantity().add(qtyToRestore));
                sellerOfferRepository.save(offer);

                if (order != null && ("AWAITING_SELLER_CONFIRMATION".equals(order.getStatus()) || "PENDING_PAYMENT".equals(order.getStatus()))) {
                    String fromStatus = order.getStatus();
                    String reasonText = "Toʻlov muddati tugaganligi sababli tizim tomonidan avtomatik bekor qilindi";

                    order.setStatus("CANCELLED");
                    order.setCancelledAt(Instant.now());
                    order.setCancellationReason(reasonText);
                    orderRepository.save(order);

                    OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                            UUID.randomUUID(),
                            order.getId(),
                            fromStatus,
                            "CANCELLED",
                            null,
                            "SYSTEM",
                            reasonText + " (avtomatik zaxira boʻshatildi)"
                    );
                    orderStatusHistoryRepository.save(history);
                }

                log.info("Successfully released expired input reservation: reservationId={}, orderId={}, offerId={}, qty={} {}, orderFromStatus={}",
                        reservationId,
                        initialRes.getOrderId(),
                        offer.getId(),
                        qtyToRestore,
                        initialRes.getUnitCode(),
                        order != null ? order.getStatus() : "NONE");

                return true;
            } else {
                // Target: Wholesale Harvest Listing
                Optional<HarvestListingEntity> preListingOpt = listingRepository.findById(initialRes.getHarvestListingId());
                if (preListingOpt.isEmpty()) {
                    log.warn("Listing {} associated with reservation {} not found; skipping", initialRes.getHarvestListingId(), reservationId);
                    return false;
                }

                HarvestListingEntity preListing = preListingOpt.get();

                // Strict Lock Acquisition Hierarchy
                // Level 1: Harvest Batch Pessimistic Write Lock
                if (preListing.getHarvestBatchId() != null) {
                    farmHarvestBridge.lockHarvestBatch(preListing.getHarvestBatchId());
                }

                // Level 2: Harvest Listing Pessimistic Write Lock
                HarvestListingEntity listing = listingRepository.findByIdForUpdate(preListing.getId())
                        .orElseThrow(() -> new IllegalStateException("Harvest listing not found under lock: " + preListing.getId()));

                // Level 3 & Level 4: Master Order Lock and Eligibility Validation
                OrderEntity order = null;
                if (initialRes.getOrderId() != null) {
                    order = orderRepository.findByIdForUpdate(initialRes.getOrderId()).orElse(null);
                }

                if (order != null) {
                    String orderStatus = order.getStatus();
                    if ("CONFIRMED".equals(orderStatus) || "PROCESSING".equals(orderStatus)
                            || "READY_FOR_DISPATCH".equals(orderStatus) || "IN_TRANSIT".equals(orderStatus)
                            || "COMPLETED".equals(orderStatus) || "DISPUTED".equals(orderStatus)) {
                        log.info("Reservation {} belongs to order {} in binding/fulfillment status {}; skipping expiration release",
                                reservationId, order.getId(), orderStatus);
                        return false;
                    }
                }

                int updatedRows = reservationRepository.transitionStatusIfActive(reservationId, "RELEASED_EXPIRED");
                if (updatedRows == 0) {
                    log.info("Reservation {} is no longer ACTIVE or was already released concurrently; skipping", reservationId);
                    return false;
                }

                if (listing.getReservedQuantity().compareTo(qtyToRestore) < 0) {
                    throw new IllegalStateException(String.format(
                            "Listing %s reservedQuantity (%s) is less than reservation quantity (%s); aborting to prevent negative balance",
                            listing.getId(), listing.getReservedQuantity(), qtyToRestore));
                }

                listing.setReservedQuantity(listing.getReservedQuantity().subtract(qtyToRestore));
                listing.setAvailableQuantity(listing.getAvailableQuantity().add(qtyToRestore));
                listingRepository.save(listing);

                if (order != null && ("AWAITING_SELLER_CONFIRMATION".equals(order.getStatus()) || "PENDING_PAYMENT".equals(order.getStatus()))) {
                    String fromStatus = order.getStatus();
                    String reasonText = "AWAITING_SELLER_CONFIRMATION".equals(fromStatus)
                            ? "Tasdiqlash muddati tugaganligi sababli tizim tomonidan avtomatik bekor qilindi"
                            : "Toʻlov muddati tugaganligi sababli tizim tomonidan avtomatik bekor qilindi";

                    order.setStatus("CANCELLED");
                    order.setCancelledAt(Instant.now());
                    order.setCancellationReason(reasonText);
                    orderRepository.save(order);

                    OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                            UUID.randomUUID(),
                            order.getId(),
                            fromStatus,
                            "CANCELLED",
                            null,
                            "SYSTEM",
                            reasonText + " (avtomatik zaxira boʻshatildi)"
                    );
                    orderStatusHistoryRepository.save(history);
                }

                log.info("Successfully released expired inventory reservation: reservationId={}, orderId={}, listingId={}, qty={} {}, orderFromStatus={}",
                        reservationId,
                        initialRes.getOrderId(),
                        listing.getId(),
                        qtyToRestore,
                        initialRes.getUnitCode(),
                        order != null ? order.getStatus() : "NONE");

                return true;
            }
        });

        return Boolean.TRUE.equals(result);
    }
}
