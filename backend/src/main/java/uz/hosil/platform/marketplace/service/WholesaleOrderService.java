package uz.hosil.platform.marketplace.service;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.FarmHarvestBridge;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateWholesaleOrderRequest;
import uz.hosil.platform.marketplace.web.OrderDetailDto;
import uz.hosil.platform.marketplace.web.OrderSummaryDto;
import uz.hosil.platform.marketplace.web.OrderStatusHistoryDto;
import uz.hosil.platform.marketplace.web.PageEnvelope;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrating wholesale order placement, atomic inventory reservation,
 * seller confirmation, cancellation, and conservation lifecycle (DEC-006, DEC-010).
 */
@Service
@Transactional
public class WholesaleOrderService {

    private static final Logger log = LoggerFactory.getLogger(WholesaleOrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InquiryRepository inquiryRepository;
    private final InquiryOfferRepository inquiryOfferRepository;
    private final HarvestListingRepository listingRepository;
    private final FarmHarvestBridge farmHarvestBridge;
    private final SellerOfferRepository sellerOfferRepository;

    @org.springframework.beans.factory.annotation.Value("${hosil.marketplace.order.confirmation-expiry-hours:24}")
    private int confirmationExpiryHours = 24;

    @org.springframework.beans.factory.annotation.Value("${hosil.marketplace.order.payment-expiry-hours:24}")
    private int paymentExpiryHours = 24;

    public WholesaleOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            InventoryReservationRepository inventoryReservationRepository,
            InquiryRepository inquiryRepository,
            InquiryOfferRepository inquiryOfferRepository,
            HarvestListingRepository listingRepository,
            FarmHarvestBridge farmHarvestBridge,
            SellerOfferRepository sellerOfferRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.inquiryRepository = inquiryRepository;
        this.inquiryOfferRepository = inquiryOfferRepository;
        this.listingRepository = listingRepository;
        this.farmHarvestBridge = farmHarvestBridge;
        this.sellerOfferRepository = sellerOfferRepository;
    }

    public record OrderCreationResult(
            OrderDetailDto orderDetail,
            boolean isIdempotentHit
    ) {}

    /**
     * Atomically creates a wholesale order from an agreed RFQ inquiry with inventory reservation.
     */
    public OrderCreationResult createOrderFromRfq(
            CreateWholesaleOrderRequest request,
            String idempotencyKey,
            UUID buyerUserId
    ) {
        // 1. Idempotency replay check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<OrderEntity> existingOrderOpt = orderRepository.findByBuyerUserIdAndIdempotencyKey(buyerUserId, idempotencyKey.trim());
            if (existingOrderOpt.isPresent()) {
                OrderEntity existingOrder = existingOrderOpt.get();
                validateWholesaleIdempotentMatch(existingOrder, request, idempotencyKey.trim());
                log.info("Idempotent hit for order creation: buyer={}, key={}", buyerUserId, idempotencyKey);
                return new OrderCreationResult(buildOrderDetailDto(existingOrder), true);
            }
        }

        // 2. Load and validate agreed RFQ inquiry
        InquiryEntity inquiry = inquiryRepository.findById(request.inquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov (inquiry) topilmadi: " + request.inquiryId()));

        if (!inquiry.getBuyerUserId().equals(buyerUserId)) {
            throw new AccessDeniedException("Faqat soʻrovni yuborgan xaridor buyurtma bera oladi");
        }

        if (!"AGREED".equals(inquiry.getStatus())) {
            throw new BusinessConflictException("Faqat kelishilgan (AGREED) soʻrovlar boʻyicha buyurtma berish mumkin. Joriy holat: " + inquiry.getStatus());
        }

        if (orderRepository.findByInquiryId(request.inquiryId()).isPresent()) {
            throw new BusinessConflictException("Ushbu soʻrov boʻyicha buyurtma allaqachon yaratilgan");
        }

        if (inquiry.getAgreedOfferId() == null) {
            throw new BusinessConflictException("Soʻrovda kelishilgan taklif mavjud emas");
        }

        InquiryOfferEntity agreedOffer = inquiryOfferRepository.findById(inquiry.getAgreedOfferId())
                .orElseThrow(() -> new ResourceNotFoundException("Kelishilgan taklif topilmadi: " + inquiry.getAgreedOfferId()));

        if (agreedOffer.getExpiresAt() != null && agreedOffer.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Kelishilgan taklif muddati tugagan");
        }

        BigDecimal quantity = agreedOffer.getQuantity();
        BigDecimal unitPrice = agreedOffer.getUnitPrice();
        BigDecimal totalAmount = quantity.multiply(unitPrice);
        String currency = agreedOffer.getCurrency();
        String deliveryTerms = agreedOffer.getDeliveryTerms();

        // 3. Strict Deterministic Locking Hierarchy:
        // Level 1: Harvest Batch Lock (Farm module bridge)
        HarvestListingEntity initialListing = listingRepository.findById(inquiry.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + inquiry.getHarvestListingId()));
        if (initialListing.getHarvestBatchId() != null) {
            farmHarvestBridge.lockHarvestBatch(initialListing.getHarvestBatchId());
        }

        // Level 2: Harvest Listing Pessimistic Write Lock
        HarvestListingEntity listing = listingRepository.findByIdForUpdate(inquiry.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + inquiry.getHarvestListingId()));

        // Re-check idempotency under lock in case a concurrent request with the same key committed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<OrderEntity> concurrentExisting = orderRepository.findByBuyerUserIdAndIdempotencyKey(buyerUserId, idempotencyKey.trim());
            if (concurrentExisting.isPresent()) {
                OrderEntity existingOrder = concurrentExisting.get();
                validateWholesaleIdempotentMatch(existingOrder, request, idempotencyKey.trim());
                log.info("Concurrent idempotent hit caught under lock: buyer={}, key={}", buyerUserId, idempotencyKey);
                return new OrderCreationResult(buildOrderDetailDto(existingOrder), true);
            }
        }

        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new BusinessConflictException("Hosildorlik e'loni faol emas: " + listing.getStatus());
        }

        if (listing.getAvailableQuantity().compareTo(quantity) < 0) {
            throw new BusinessConflictException("INSUFFICIENT_STOCK_AVAILABLE: Yetarli zaxira mavjud emas. Talab: " + quantity + ", Mavjud: " + listing.getAvailableQuantity());
        }

        // Atomic inventory adjustment (Quantity conservation: available -> reserved)
        listing.setAvailableQuantity(listing.getAvailableQuantity().subtract(quantity));
        listing.setReservedQuantity(listing.getReservedQuantity().add(quantity));
        listingRepository.save(listing);

        // Level 3: Master Order (Parent entity persisted and flushed before child references)
        UUID orderId = UUID.randomUUID();
        Instant confirmationExpiresAt = Instant.now().plus(Duration.ofHours(confirmationExpiryHours));
        String orderNumber = generateOrderNumber();
        String deliveryType = (request.deliveryType() != null && !request.deliveryType().isBlank())
                ? request.deliveryType().trim().toUpperCase()
                : "BUYER_ARRANGES";

        OrderEntity order = new OrderEntity(
                orderId,
                orderNumber,
                buyerUserId,
                inquiry.getSellerUserId(),
                inquiry.getId(),
                agreedOffer.getId(),
                listing.getId(),
                "HARVEST_PURCHASE_REQUEST",
                totalAmount,
                currency,
                "AWAITING_SELLER_CONFIRMATION",
                deliveryType,
                request.deliveryAddress(),
                deliveryTerms,
                request.notes(),
                confirmationExpiresAt,
                idempotencyKey != null ? idempotencyKey.trim() : null
        );
        order = orderRepository.saveAndFlush(order);

        // Level 4: Inventory Reservation, Item Snapshot, and Status Audit History
        InventoryReservationEntity reservation = new InventoryReservationEntity(
                UUID.randomUUID(),
                orderId,
                listing.getId(),
                quantity,
                listing.getUnitCode(),
                confirmationExpiresAt,
                "ACTIVE"
        );
        inventoryReservationRepository.save(reservation);

        OrderItemEntity orderItem = new OrderItemEntity(
                UUID.randomUUID(),
                orderId,
                listing.getId(),
                listing.getTitle(),
                listing.getCropType(),
                quantity,
                listing.getUnitCode(),
                unitPrice,
                totalAmount,
                currency
        );
        orderItemRepository.save(orderItem);

        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                null,
                "AWAITING_SELLER_CONFIRMATION",
                buyerUserId,
                "BUYER",
                "Buyurtma RFQ asosida shakllantirildi va zaxira band qilindi"
        );
        orderStatusHistoryRepository.save(history);

        log.info("Order created successfully: orderId={}, orderNumber={}, buyer={}, seller={}, total={}",
                orderId, orderNumber, buyerUserId, inquiry.getSellerUserId(), totalAmount);

        return new OrderCreationResult(
                OrderDetailDto.fromEntity(order, List.of(orderItem), reservation, List.of(history)),
                false
        );
    }

    /**
     * Seller confirms wholesale order, transitioning it to binding status CONFIRMED (DEC-006).
     */
    public OrderDetailDto confirmOrder(UUID orderId, UUID sellerUserId) {
        OrderEntity preOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!preOrder.getSellerUserId().equals(sellerUserId)) {
            throw new AccessDeniedException("Faqat sotuvchi buyurtmani tasdiqlashi mumkin");
        }

        if (!"AWAITING_SELLER_CONFIRMATION".equals(preOrder.getStatus())) {
            throw new BusinessConflictException("Buyurtma tasdiqlash kutilayotgan holatda emas: " + preOrder.getStatus());
        }

        if (preOrder.getConfirmationExpiresAt() != null && preOrder.getConfirmationExpiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Tasdiqlash muddati tugagan");
        }

        // Strict Lock Acquisition Hierarchy:
        // Level 1: Inventory Source Lock
        if (preOrder.getHarvestListingId() != null) {
            HarvestListingEntity initialListing = listingRepository.findById(preOrder.getHarvestListingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + preOrder.getHarvestListingId()));
            if (initialListing.getHarvestBatchId() != null) {
                farmHarvestBridge.lockHarvestBatch(initialListing.getHarvestBatchId());
            }
            listingRepository.findByIdForUpdate(preOrder.getHarvestListingId());
        }

        // Level 2: Master Order Pessimistic Write Lock
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        // Re-check order status under lock
        if (!"AWAITING_SELLER_CONFIRMATION".equals(order.getStatus())) {
            throw new BusinessConflictException("Buyurtma tasdiqlash kutilayotgan holatda emas: " + order.getStatus());
        }

        if (order.getConfirmationExpiresAt() != null && order.getConfirmationExpiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Tasdiqlash muddati tugagan");
        }

        // Validate active reservation
        InventoryReservationEntity reservation = inventoryReservationRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessConflictException("Buyurtma uchun zaxira topilmadi"));
        if (!"ACTIVE".equals(reservation.getStatus())) {
            throw new BusinessConflictException("Buyurtma zaxirasi faol emas: " + reservation.getStatus());
        }

        order.setStatus("CONFIRMED");
        order.setConfirmedAt(Instant.now());
        order.setPaymentExpiresAt(Instant.now().plus(Duration.ofHours(paymentExpiryHours)));
        orderRepository.save(order);

        reservation.setExpiresAt(order.getPaymentExpiresAt());
        inventoryReservationRepository.save(reservation);

        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                order.getId(),
                "AWAITING_SELLER_CONFIRMATION",
                "CONFIRMED",
                sellerUserId,
                "SELLER",
                "Sotuvchi buyurtmani tasdiqladi. Shartnoma majburiy kuchga kirdi."
        );
        orderStatusHistoryRepository.save(history);

        log.info("Order confirmed: orderId={}, seller={}", orderId, sellerUserId);
        return buildOrderDetailDto(order);
    }

    /**
     * Seller rejects wholesale order, atomically releasing the reserved stock back to available.
     */
    public OrderDetailDto rejectOrder(UUID orderId, String reason, UUID sellerUserId) {
        OrderEntity preOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!preOrder.getSellerUserId().equals(sellerUserId)) {
            throw new AccessDeniedException("Faqat sotuvchi buyurtmani rad eta oladi");
        }

        if (!"AWAITING_SELLER_CONFIRMATION".equals(preOrder.getStatus())) {
            throw new BusinessConflictException("Buyurtma rad etish mumkin boʻlgan holatda emas: " + preOrder.getStatus());
        }

        // Strict Lock Acquisition Hierarchy:
        // Level 1: Harvest Batch & Listing Lock
        HarvestListingEntity initialListing = listingRepository.findById(preOrder.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + preOrder.getHarvestListingId()));
        if (initialListing.getHarvestBatchId() != null) {
            farmHarvestBridge.lockHarvestBatch(initialListing.getHarvestBatchId());
        }

        HarvestListingEntity listing = listingRepository.findByIdForUpdate(preOrder.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + preOrder.getHarvestListingId()));

        // Level 2: Order Pessimistic Write Lock
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!"AWAITING_SELLER_CONFIRMATION".equals(order.getStatus())) {
            throw new BusinessConflictException("Buyurtma rad etish mumkin boʻlgan holatda emas: " + order.getStatus());
        }

        // Level 3: Inventory Reservation CAS status transition
        Optional<InventoryReservationEntity> resOpt = inventoryReservationRepository.findByOrderId(order.getId());
        if (resOpt.isPresent()) {
            InventoryReservationEntity reservation = resOpt.get();
            int updated = inventoryReservationRepository.transitionStatusIfActive(reservation.getId(), "RELEASED_CANCELLED");
            if (updated > 0) {
                // Stock restoration ONLY if atomic CAS succeeded (row was ACTIVE)
                listing.setReservedQuantity(listing.getReservedQuantity().subtract(reservation.getReservedQuantity()));
                listing.setAvailableQuantity(listing.getAvailableQuantity().add(reservation.getReservedQuantity()));
                listingRepository.save(listing);
                log.info("Stock restored to listing {} on rejection: qty={}", listing.getId(), reservation.getReservedQuantity());
            } else {
                log.info("Reservation {} was already transitioned concurrently; skipping stock restoration on rejection", reservation.getId());
            }
        }

        String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Sotuvchi tomonidan rad etildi";
        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        order.setRejectionReason(effectiveReason);
        orderRepository.save(order);

        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                order.getId(),
                "AWAITING_SELLER_CONFIRMATION",
                "CANCELLED",
                sellerUserId,
                "SELLER",
                effectiveReason
        );
        orderStatusHistoryRepository.save(history);

        log.info("Order rejected: orderId={}, seller={}, reason={}", orderId, sellerUserId, effectiveReason);
        return buildOrderDetailDto(order);
    }

    /**
     * Buyer or seller cancels wholesale order according to stage-gated rules (DEC-010).
     */
    public OrderDetailDto cancelOrder(UUID orderId, String reason, UUID callerUserId) {
        OrderEntity preOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        boolean isBuyer = preOrder.getBuyerUserId().equals(callerUserId);
        boolean isSeller = preOrder.getSellerUserId().equals(callerUserId);

        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Faqat buyurtma ishtirokchilari bekor qila oladi");
        }

        String preStatus = preOrder.getStatus();
        String actorRole = isBuyer ? "BUYER" : "SELLER";

        if (isBuyer) {
            if (!"AWAITING_SELLER_CONFIRMATION".equals(preStatus) && !"PENDING_PAYMENT".equals(preStatus)) {
                throw new BusinessConflictException("Xaridor ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + preStatus);
            }
        } else {
            if ("READY_FOR_DISPATCH".equals(preStatus) || "IN_TRANSIT".equals(preStatus)
                    || "COMPLETED".equals(preStatus) || "CANCELLED".equals(preStatus) || "DISPUTED".equals(preStatus)) {
                throw new BusinessConflictException("Sotuvchi ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + preStatus);
            }
        }

        // Strict Lock Acquisition Hierarchy:
        if ("INPUT_DIRECT_CHECKOUT".equals(preOrder.getOrderFlowType())) {
            List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(orderId);
            List<UUID> offerIds = reservations.stream()
                    .map(InventoryReservationEntity::getSellerOfferId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            // Level 1: Lock seller offers in ascending UUID order
            Map<UUID, SellerOfferEntity> lockedOffers = offerIds.isEmpty() ? Map.of() :
                    sellerOfferRepository.findAllByIdInForUpdate(offerIds).stream()
                            .collect(Collectors.toMap(SellerOfferEntity::getId, o -> o));

            // Level 2: Lock order
            OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

            String fromStatus = order.getStatus();
            if (isBuyer) {
                if (!"AWAITING_SELLER_CONFIRMATION".equals(fromStatus) && !"PENDING_PAYMENT".equals(fromStatus)) {
                    throw new BusinessConflictException("Xaridor ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + fromStatus);
                }
            } else {
                if ("READY_FOR_DISPATCH".equals(fromStatus) || "IN_TRANSIT".equals(fromStatus)
                        || "COMPLETED".equals(fromStatus) || "CANCELLED".equals(fromStatus) || "DISPUTED".equals(fromStatus)) {
                    throw new BusinessConflictException("Sotuvchi ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + fromStatus);
                }
            }

            // Level 3: Atomic CAS status transition on reservations
            for (InventoryReservationEntity reservation : reservations) {
                int updated = inventoryReservationRepository.transitionStatusIfActive(reservation.getId(), "RELEASED_CANCELLED");
                if (updated > 0 && reservation.getSellerOfferId() != null) {
                    SellerOfferEntity offer = lockedOffers.get(reservation.getSellerOfferId());
                    if (offer != null) {
                        offer.setReservedQuantity(offer.getReservedQuantity().subtract(reservation.getReservedQuantity()));
                        offer.setAvailableQuantity(offer.getAvailableQuantity().add(reservation.getReservedQuantity()));
                        sellerOfferRepository.save(offer);
                        log.info("Stock restored to seller offer {} on cancellation: qty={}", offer.getId(), reservation.getReservedQuantity());
                    }
                } else {
                    log.info("Reservation {} was already transitioned concurrently; skipping stock restoration", reservation.getId());
                }
            }

            String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Buyurtma bekor qilindi";
            order.setStatus("CANCELLED");
            order.setCancelledAt(Instant.now());
            order.setCancellationReason(effectiveReason);
            orderRepository.save(order);

            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    fromStatus,
                    "CANCELLED",
                    callerUserId,
                    actorRole,
                    effectiveReason
            );
            orderStatusHistoryRepository.save(history);

            log.info("Input order cancelled: orderId={}, actor={}, role={}, reason={}", orderId, callerUserId, actorRole, effectiveReason);
            return buildOrderDetailDto(order);

        } else {
            // Level 1: Harvest Batch & Listing Lock
            HarvestListingEntity initialListing = listingRepository.findById(preOrder.getHarvestListingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + preOrder.getHarvestListingId()));
            if (initialListing.getHarvestBatchId() != null) {
                farmHarvestBridge.lockHarvestBatch(initialListing.getHarvestBatchId());
            }

            HarvestListingEntity listing = listingRepository.findByIdForUpdate(preOrder.getHarvestListingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hosildorlik e'loni topilmadi: " + preOrder.getHarvestListingId()));

            // Level 2: Lock order
            OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

            String fromStatus = order.getStatus();
            if (isBuyer) {
                if (!"AWAITING_SELLER_CONFIRMATION".equals(fromStatus) && !"PENDING_PAYMENT".equals(fromStatus)) {
                    throw new BusinessConflictException("Xaridor ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + fromStatus);
                }
            } else {
                if ("READY_FOR_DISPATCH".equals(fromStatus) || "IN_TRANSIT".equals(fromStatus)
                        || "COMPLETED".equals(fromStatus) || "CANCELLED".equals(fromStatus) || "DISPUTED".equals(fromStatus)) {
                    throw new BusinessConflictException("Sotuvchi ushbu bosqichda buyurtmani bekor qila olmaydi. Joriy holat: " + fromStatus);
                }
            }

            // Level 3: Atomic CAS status transition on reservations
            List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(order.getId());
            for (InventoryReservationEntity reservation : reservations) {
                int updated = inventoryReservationRepository.transitionStatusIfActive(reservation.getId(), "RELEASED_CANCELLED");
                if (updated > 0) {
                    listing.setReservedQuantity(listing.getReservedQuantity().subtract(reservation.getReservedQuantity()));
                    listing.setAvailableQuantity(listing.getAvailableQuantity().add(reservation.getReservedQuantity()));
                    listingRepository.save(listing);
                    log.info("Stock restored to harvest listing {} on cancellation: qty={}", listing.getId(), reservation.getReservedQuantity());
                } else {
                    log.info("Reservation {} was already transitioned concurrently; skipping stock restoration", reservation.getId());
                }
            }

            String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Buyurtma bekor qilindi";
            order.setStatus("CANCELLED");
            order.setCancelledAt(Instant.now());
            order.setCancellationReason(effectiveReason);
            orderRepository.save(order);

            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                order.getId(),
                fromStatus,
                "CANCELLED",
                callerUserId,
                actorRole,
                effectiveReason
        );
        orderStatusHistoryRepository.save(history);

        log.info("Wholesale order cancelled: orderId={}, actor={}, role={}, reason={}", orderId, callerUserId, actorRole, effectiveReason);
        return buildOrderDetailDto(order);
    }
}

    /**
     * Retrieves detailed order record for authorized buyer or seller.
     */
    @Transactional(readOnly = true)
    public OrderDetailDto getOrderDetail(UUID orderId, UUID callerUserId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!order.getBuyerUserId().equals(callerUserId) && !order.getSellerUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Ushbu buyurtmani koʻrish huquqi yoʻq");
        }

        return buildOrderDetailDto(order);
    }

    /**
     * Retrieves chronological status transition history for an order.
     */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryDto> getOrderHistory(UUID orderId, UUID callerUserId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!order.getBuyerUserId().equals(callerUserId) && !order.getSellerUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Ushbu buyurtma tarixini koʻrish huquqi yoʻq");
        }

        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(OrderStatusHistoryDto::fromEntity)
                .toList();
    }

    /**
     * Lists orders for the authenticated caller with optional role and status filters.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<OrderSummaryDto> listMyOrders(
            String role,
            String status,
            Pageable pageable,
            UUID callerUserId
    ) {
        Specification<OrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if ("BUYER".equalsIgnoreCase(role)) {
                predicates.add(cb.equal(root.get("buyerUserId"), callerUserId));
            } else if ("SELLER".equalsIgnoreCase(role)) {
                predicates.add(cb.equal(root.get("sellerUserId"), callerUserId));
            } else {
                predicates.add(cb.or(
                        cb.equal(root.get("buyerUserId"), callerUserId),
                        cb.equal(root.get("sellerUserId"), callerUserId)
                ));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<OrderEntity> page = orderRepository.findAll(spec, pageable);

        Map<UUID, String> cropTypeMap = page.getContent().stream()
                .map(OrderEntity::getId)
                .collect(Collectors.toMap(
                        id -> id,
                        id -> orderItemRepository.findByOrderId(id).stream()
                                .findFirst()
                                .map(item -> item.getCropType() != null && !item.getCropType().isBlank()
                                        ? item.getCropType()
                                        : item.getItemSnapshotTitle())
                                .orElse("Qishloq xo'jaligi mahsuloti")
                ));

        Page<OrderSummaryDto> dtoPage = page.map(order ->
                OrderSummaryDto.fromEntity(order, cropTypeMap.getOrDefault(order.getId(), "Qishloq xo'jaligi mahsuloti"))
        );

        return PageEnvelope.from(dtoPage);
    }

    private OrderDetailDto buildOrderDetailDto(OrderEntity order) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(order.getId());
        List<OrderStatusHistoryEntity> history = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return OrderDetailDto.fromEntity(order, items, reservations, history);
    }

    private String generateOrderNumber() {
        String datePart = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now());
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    private void validateWholesaleIdempotentMatch(OrderEntity existingOrder, CreateWholesaleOrderRequest request, String key) {
        String reqDelivery = (request.deliveryType() != null && !request.deliveryType().isBlank())
                ? request.deliveryType().trim().toUpperCase()
                : "BUYER_ARRANGES";
        if (!existingOrder.getDeliveryType().equals(reqDelivery)) {
            throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
        }

        if (!java.util.Objects.equals(existingOrder.getInquiryId(), request.inquiryId())) {
            throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
        }
    }
}
