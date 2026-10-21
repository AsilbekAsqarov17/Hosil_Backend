package uz.hosil.platform.marketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateInputOrderRequest;
import uz.hosil.platform.marketplace.web.OrderDetailDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing agricultural-input order placement, deterministic offer locking,
 * atomic inventory reservations, and server-side pricing (DEC-005, DEC-007, DEC-010).
 */
@Service
public class InputOrderService {

    private static final Logger log = LoggerFactory.getLogger(InputOrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final SellerOfferRepository sellerOfferRepository;
    private final CatalogProductRepository catalogProductRepository;
    private final int paymentExpiryHours;

    public InputOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            InventoryReservationRepository inventoryReservationRepository,
            SellerOfferRepository sellerOfferRepository,
            CatalogProductRepository catalogProductRepository,
            @Value("${hosil.marketplace.order.payment-expiry-hours:24}") int paymentExpiryHours
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.sellerOfferRepository = sellerOfferRepository;
        this.catalogProductRepository = catalogProductRepository;
        this.paymentExpiryHours = paymentExpiryHours;
    }

    /**
     * Places an agricultural-input order from one or more seller offers belonging to the same seller (DEC-005).
     *
     * @param request validated input checkout payload
     * @param idempotencyKey optional idempotency header
     * @param buyerUserId authenticated principal ID
     * @return creation result containing OrderDetailDto and idempotency indicator
     */
    @Transactional
    public OrderCreationResult createInputOrder(
            CreateInputOrderRequest request,
            String idempotencyKey,
            UUID buyerUserId
    ) {
        // Step 1: Enforce Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<OrderEntity> existing = orderRepository.findByBuyerUserIdAndIdempotencyKey(
                    buyerUserId, idempotencyKey.trim()
            );
            if (existing.isPresent()) {
                OrderEntity existingOrder = existing.get();
                validateIdempotentRequestMatch(existingOrder, request, idempotencyKey.trim());
                log.info("Idempotent hit for input order: buyer={}, key={}", buyerUserId, idempotencyKey.trim());
                return new OrderCreationResult(buildOrderDetailDto(existingOrder), true);
            }
        }

        // Step 2: Validate payload items
        if (request.items() == null || request.items().isEmpty()) {
            throw new ValidationException("Buyurtmada kamida bitta mahsulot boʻlishi kerak");
        }

        Set<UUID> seenOfferIds = new HashSet<>();
        for (CreateInputOrderRequest.InputOrderItemRequest item : request.items()) {
            if (!seenOfferIds.add(item.sellerOfferId())) {
                throw new ValidationException("Bitta buyurtmada ayni bir taklifni takrorlab kiritish mumkin emas: " + item.sellerOfferId());
            }
        }

        // Step 3: Deterministic Lock Acquisition (sorted by offer UUID ascending to prevent deadlocks)
        List<UUID> sortedOfferIds = request.items().stream()
                .map(CreateInputOrderRequest.InputOrderItemRequest::sellerOfferId)
                .sorted()
                .toList();

        List<SellerOfferEntity> lockedOffers = sellerOfferRepository.findAllByIdInForUpdate(sortedOfferIds);
        if (lockedOffers.size() != sortedOfferIds.size()) {
            throw new ResourceNotFoundException("Bir yoki bir nechta taklif topilmadi");
        }

        // Step 3.1: Re-check idempotency under lock in case a concurrent request with the same key committed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<OrderEntity> concurrentExisting = orderRepository.findByBuyerUserIdAndIdempotencyKey(
                    buyerUserId, idempotencyKey.trim()
            );
            if (concurrentExisting.isPresent()) {
                OrderEntity existingOrder = concurrentExisting.get();
                validateIdempotentRequestMatch(existingOrder, request, idempotencyKey.trim());
                log.info("Concurrent idempotent hit caught under lock: buyer={}, key={}", buyerUserId, idempotencyKey.trim());
                return new OrderCreationResult(buildOrderDetailDto(existingOrder), true);
            }
        }

        Map<UUID, SellerOfferEntity> offerMap = lockedOffers.stream()
                .collect(Collectors.toMap(SellerOfferEntity::getId, o -> o));

        // Step 4: Validate single-seller policy (MVP scope)
        UUID sellerUserId = lockedOffers.get(0).getSellerUserId();
        for (SellerOfferEntity offer : lockedOffers) {
            if (!offer.getSellerUserId().equals(sellerUserId)) {
                throw new BusinessConflictException("Barcha mahsulotlar bitta sotuvchiga tegishli boʻlishi kerak (Single-seller order policy). Turli sotuvchilar aniqlandi.");
            }
        }

        // Step 5: Reject self-ordering
        if (sellerUserId.equals(buyerUserId)) {
            throw new BusinessConflictException("Foydalanuvchi oʻz taklifiga buyurtma bera olmaydi (Self-ordering is forbidden)");
        }

        // Step 6: Validate offer statuses, stock, units, MOQ, and step quantities
        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = lockedOffers.get(0).getCurrency();

        for (CreateInputOrderRequest.InputOrderItemRequest item : request.items()) {
            SellerOfferEntity offer = offerMap.get(item.sellerOfferId());

            if (!"ACTIVE".equals(offer.getStatus())) {
                throw new BusinessConflictException("Sotuvchi taklifi faol emas (status=" + offer.getStatus() + "): " + offer.getId());
            }

            if (!offer.getUnitCode().equalsIgnoreCase(item.unitCode().trim())) {
                throw new ValidationException(String.format(
                        "Oʻlchov birligi mos emas. Taklif birligi: %s, soʻralgan birlik: %s",
                        offer.getUnitCode(), item.unitCode()
                ));
            }

            BigDecimal qty = item.quantity();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Buyurtma miqdori musbat boʻlishi kerak: " + qty);
            }

            if (qty.stripTrailingZeros().scale() > 3) {
                throw new ValidationException("Buyurtma miqdori koʻpi bilan 3 ta kasr xonasiga ega boʻlishi mumkin: " + qty);
            }

            if (qty.compareTo(offer.getMinOrderQuantity()) < 0) {
                throw new ValidationException(String.format(
                        "Buyurtma miqdori minimal buyurtma miqdoridan kam (MOQ: %s %s, soʻralgan: %s %s)",
                        offer.getMinOrderQuantity(), offer.getUnitCode(), qty, item.unitCode()
                ));
            }

            BigDecimal excess = qty.subtract(offer.getMinOrderQuantity());
            if (excess.remainder(offer.getStepQuantity()).compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(String.format(
                        "Buyurtma miqdori qadami mos emas. Ruxsat etilgan qadam: %s %s",
                        offer.getStepQuantity(), offer.getUnitCode()
                ));
            }

            if (offer.getAvailableQuantity().compareTo(qty) < 0) {
                throw new BusinessConflictException(String.format(
                        "Yetarli miqdorda mahsulot mavjud emas. Mavjud: %s %s, soʻralgan: %s %s",
                        offer.getAvailableQuantity(), offer.getUnitCode(), qty, item.unitCode()
                ));
            }

            BigDecimal lineSubtotal = qty.multiply(offer.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineSubtotal);
        }

        // Step 7: Create Master Order in PENDING_PAYMENT status
        UUID orderId = UUID.randomUUID();
        String orderNumber = generateOrderNumber();
        Instant now = Instant.now();
        Instant paymentExpiresAt = now.plus(Duration.ofHours(paymentExpiryHours));
        String deliveryType = (request.deliveryType() != null && !request.deliveryType().isBlank())
                ? request.deliveryType().trim().toUpperCase()
                : "BUYER_ARRANGES";

        OrderEntity order = new OrderEntity(
                orderId,
                orderNumber,
                buyerUserId,
                sellerUserId,
                "INPUT_DIRECT_CHECKOUT",
                totalAmount,
                currency,
                "PENDING_PAYMENT",
                deliveryType,
                request.deliveryAddress(),
                request.deliveryTerms(),
                request.notes(),
                paymentExpiresAt,
                idempotencyKey != null ? idempotencyKey.trim() : null
        );
        order = orderRepository.saveAndFlush(order);

        // Step 8: Create Order Items, Deduct Stock, and Create Inventory Reservations atomically
        List<OrderItemEntity> orderItems = new ArrayList<>();
        List<InventoryReservationEntity> reservations = new ArrayList<>();

        for (CreateInputOrderRequest.InputOrderItemRequest item : request.items()) {
            SellerOfferEntity offer = offerMap.get(item.sellerOfferId());
            CatalogProductEntity product = catalogProductRepository.findById(offer.getCatalogProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Katalog mahsuloti topilmadi: " + offer.getCatalogProductId()));

            String titleSnapshot = product.getNameUz();
            if (product.getBrandName() != null && !product.getBrandName().isBlank()) {
                titleSnapshot += " (" + product.getBrandName().trim() + ")";
            }

            BigDecimal lineSubtotal = item.quantity().multiply(offer.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);

            OrderItemEntity orderItem = new OrderItemEntity(
                    UUID.randomUUID(),
                    orderId,
                    offer.getId(),
                    product.getId(),
                    titleSnapshot,
                    item.quantity(),
                    offer.getUnitCode(),
                    offer.getUnitPrice(),
                    lineSubtotal,
                    currency
            );
            orderItemRepository.save(orderItem);
            orderItems.add(orderItem);

            // Update Offer stock balances: available -= qty, reserved += qty (Strict conservation)
            offer.setAvailableQuantity(offer.getAvailableQuantity().subtract(item.quantity()));
            offer.setReservedQuantity(offer.getReservedQuantity().add(item.quantity()));
            sellerOfferRepository.save(offer);

            // Create temporary atomic stock reservation
            InventoryReservationEntity reservation = new InventoryReservationEntity(
                    UUID.randomUUID(),
                    orderId,
                    item.quantity(),
                    offer.getUnitCode(),
                    paymentExpiresAt,
                    "ACTIVE",
                    offer.getId()
            );
            inventoryReservationRepository.save(reservation);
            reservations.add(reservation);
        }

        // Step 9: Append Status History
        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                null,
                "PENDING_PAYMENT",
                buyerUserId,
                "BUYER",
                "Qishloq xoʻjaligi vositalari uchun toʻgʻridan-toʻgʻri buyurtma yaratildi va zaxira band qilindi"
        );
        orderStatusHistoryRepository.save(history);

        log.info("Input order placed successfully: orderId={}, orderNumber={}, buyer={}, seller={}, itemsCount={}, total={}",
                orderId, orderNumber, buyerUserId, sellerUserId, orderItems.size(), totalAmount);

        return new OrderCreationResult(
                OrderDetailDto.fromEntity(order, orderItems, reservations, List.of(history)),
                false
        );
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
        return "ORD-INP-" + datePart + "-" + randomPart;
    }

    private void validateIdempotentRequestMatch(OrderEntity existingOrder, CreateInputOrderRequest request, String key) {
        String reqDelivery = (request.deliveryType() != null && !request.deliveryType().isBlank())
                ? request.deliveryType().trim().toUpperCase()
                : "BUYER_ARRANGES";
        if (!existingOrder.getDeliveryType().equals(reqDelivery)) {
            throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
        }

        List<OrderItemEntity> existingItems = orderItemRepository.findByOrderId(existingOrder.getId());
        if (request.items() == null || existingItems.size() != request.items().size()) {
            throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
        }

        Map<UUID, OrderItemEntity> itemMap = existingItems.stream()
                .filter(i -> i.getSellerOfferId() != null)
                .collect(Collectors.toMap(OrderItemEntity::getSellerOfferId, i -> i));

        for (CreateInputOrderRequest.InputOrderItemRequest reqItem : request.items()) {
            OrderItemEntity existingItem = itemMap.get(reqItem.sellerOfferId());
            if (existingItem == null) {
                throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
            }
            if (existingItem.getQuantity().compareTo(reqItem.quantity()) != 0) {
                throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
            }
            if (!existingItem.getUnitCode().equalsIgnoreCase(reqItem.unitCode().trim())) {
                throw new BusinessConflictException("Idempotency-Key allaqachon boshqa parametrlar bilan yaratilgan buyurtma uchun ishlatilgan: " + key);
            }
        }
    }

    public record OrderCreationResult(OrderDetailDto orderDetail, boolean isIdempotentHit) {}
}
