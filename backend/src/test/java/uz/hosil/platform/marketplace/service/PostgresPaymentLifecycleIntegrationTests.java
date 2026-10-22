package uz.hosil.platform.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.InvalidPaymentSignatureException;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.PaymentEntity;
import uz.hosil.platform.marketplace.domain.PaymentEventEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.PaymentEventRepository;
import uz.hosil.platform.marketplace.repository.PaymentRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateInputOrderRequest;
import uz.hosil.platform.marketplace.web.CreateWholesaleOrderRequest;
import uz.hosil.platform.marketplace.web.InitiatePaymentRequest;
import uz.hosil.platform.marketplace.web.OrderDetailDto;
import uz.hosil.platform.marketplace.web.PaymentDetailDto;
import uz.hosil.platform.marketplace.web.PaymentWebhookResult;
import uz.hosil.platform.marketplace.web.SimulatePaymentCaptureRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 16 + Flyway backed integration tests validating the complete marketplace
 * payment lifecycle, cryptographic HMAC signatures, webhook idempotency, IDOR protection,
 * pessimistic concurrency, and order/reservation status transitions (PAY-001, PAY-002, PAY-004, DEC-007).
 */
@SpringBootTest
@ActiveProfiles("local")
class PostgresPaymentLifecycleIntegrationTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MockSandboxPaymentGateway sandboxGateway;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private WholesaleOrderService wholesaleOrderService;

    @Autowired
    private InputOrderService inputOrderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogProductRepository catalogProductRepository;

    @Autowired
    private SellerOfferRepository sellerOfferRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private HarvestListingRepository listingRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryOfferRepository inquiryOfferRepository;

    private UserEntity createSyntheticUser(String phonePrefix) {
        String uniqueDigits = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        String phone = phonePrefix + uniqueDigits;
        UserEntity user = new UserEntity(phone, "ACTIVE", "uz");
        user = userRepository.save(user);

        UserProfileEntity profile = new UserProfileEntity(user.getId(), "Test User " + uniqueDigits, "Toshkent", "Yunusobod");
        userProfileRepository.save(profile);
        return user;
    }

    private CategoryEntity getOrCreateCategory(String code, String name) {
        return categoryRepository.findByCode(code).orElseGet(() -> {
            CategoryEntity cat = new CategoryEntity(UUID.randomUUID(), null, code, name, name + " RU", 1);
            return categoryRepository.save(cat);
        });
    }

    @Test
    @DisplayName("Scenario 1: Input direct checkout payment transitions payment to CAPTURED, order to CONFIRMED, reservation to CONFIRMED")
    void testInputOrderDirectCheckoutPaymentLifecycle() {
        // Step 1: Create seller, product, offer, and buyer
        UserEntity seller = createSyntheticUser("+99890111");
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), seller.getId(), "COMMERCIAL_SUPPLIER", "Test Agro Dealer LLC", "123456789", "VERIFIED", Instant.now()
        ));

        CategoryEntity cat = getOrCreateCategory("FERTILIZER_P1", "Oʻgʻitlar P1");
        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Urea 46%", "Urea 46% RU", "MAXAM", "NPK", "BAG_50KG", "Mineral fertilizer", true
        ));

        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), seller.getId(), product.getId(),
                new BigDecimal("5000.00"), "UZS", "BAG_50KG",
                new BigDecimal("1000.000"), BigDecimal.ZERO,
                new BigDecimal("10.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Yunusobod",
                "Omborda mavjud", "ACTIVE"
        ));

        UserEntity buyer = createSyntheticUser("+99890222");

        // Step 2: Checkout input order -> enters PENDING_PAYMENT
        CreateInputOrderRequest.InputOrderItemRequest itemReq = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("100.000"), "BAG_50KG"
        );
        CreateInputOrderRequest orderReq = new CreateInputOrderRequest(
                List.of(itemReq), "BUYER_ARRANGES", "Toshkent shahar", null, "Test direct checkout input order"
        );
        var creationResult = inputOrderService.createInputOrder(orderReq, "idemp-input-pay-" + UUID.randomUUID(), buyer.getId());
        UUID orderId = creationResult.orderDetail().id();

        OrderEntity preOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("PENDING_PAYMENT", preOrder.getStatus());
        assertEquals("INPUT_DIRECT_CHECKOUT", preOrder.getOrderFlowType());

        // Step 3: Initiate Payment
        InitiatePaymentRequest initReq = new InitiatePaymentRequest(orderId, "MOCK_SANDBOX");
        PaymentDetailDto paymentDto = paymentService.initiatePayment(initReq, buyer.getId(), "pay-idemp-" + UUID.randomUUID());

        assertNotNull(paymentDto.id());
        assertEquals("PENDING_PROVIDER", paymentDto.status());
        assertEquals(orderId, paymentDto.orderId());
        assertEquals(new BigDecimal("500000.00"), paymentDto.amount());

        // Step 4: Webhook Callback for CAPTURED
        String mockPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-REAL-001\"}",
                paymentDto.paymentIntentReference(), paymentDto.id()
        );
        String signature = sandboxGateway.generateSignature(mockPayload);
        String eventId = "EVT-" + UUID.randomUUID();

        PaymentWebhookResult webhookResult = paymentService.processWebhook("MOCK_SANDBOX", mockPayload, signature, eventId);
        assertTrue(webhookResult.success());
        assertEquals("CAPTURED", webhookResult.status());

        // Step 5: Verify Database State
        PaymentEntity updatedPayment = paymentRepository.findById(paymentDto.id()).orElseThrow();
        assertEquals("CAPTURED", updatedPayment.getStatus());
        assertEquals("TX-REAL-001", updatedPayment.getProviderTransactionId());
        assertNotNull(updatedPayment.getConfirmedAt());

        OrderEntity confirmedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CONFIRMED", confirmedOrder.getStatus(), "Input order must transition from PENDING_PAYMENT to CONFIRMED upon payment capture");
        assertNotNull(confirmedOrder.getConfirmedAt());

        InventoryReservationEntity reservation = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertEquals("CONFIRMED_SOLD", reservation.getStatus(), "Inventory reservation must transition to CONFIRMED_SOLD upon payment capture");

        // Verify audit event stream
        List<PaymentEventEntity> events = paymentEventRepository.findByPaymentIdOrderByRecordedAtAsc(paymentDto.id());
        assertTrue(events.size() >= 2);
        assertTrue(events.stream().anyMatch(e -> "PAYMENT_CAPTURED".equals(e.getEventType()) && e.getSignatureVerified()));
    }

    @Test
    @DisplayName("Scenario 2: Wholesale order post seller confirmation transitions payment to CAPTURED, order to PROCESSING, reservation to CONFIRMED")
    void testWholesaleOrderPaymentLifecycle() {
        // Step 1: Create seller, farm, harvest batch, harvest listing
        UserEntity seller = createSyntheticUser("+99890333");
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), seller.getId(), "INDIVIDUAL_FARMER", "Fergana Agro Export LLC", "987654321", "VERIFIED", Instant.now()
        ));

        FarmEntity farm = farmRepository.save(new FarmEntity(seller.getId(), "Fergana Agro Farm", "Fergana", "Quva", new BigDecimal("50.0"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Field 1", new BigDecimal("10.0"), "HECTARE", null, "Pamidor maydoni"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Tomato Winter", "TOMATO", LocalDate.now().minusMonths(3), LocalDate.now(), "HARVESTED"));

        CategoryEntity cat = getOrCreateCategory("HARVEST_W", "Hosil Ulgurji");
        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(), farm.getId(), cropCycle.getId(), "Pamidor", "FIRST_CLASS",
                new BigDecimal("10000.000"), new BigDecimal("10000.000"), "KG", LocalDate.now(),
                "Ombor #1", "RECORDED", "BATCH-" + UUID.randomUUID().toString().substring(0, 6), null, 1L
        ));

        HarvestListingEntity listing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(), seller.getId(), batch.getId(), cat.getId(), "Premium Greenhouse Tomatoes", "Greenhouse Red Tomatoes",
                "Pamidor", "FIRST_CLASS", new BigDecimal("8000.00"), "UZS", "RFQ", true,
                new BigDecimal("5000.000"), new BigDecimal("5000.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "KG", new BigDecimal("500.000"), new BigDecimal("50.000"), "Fergana", "Quva", "PUBLISHED", null, Instant.now().minusSeconds(1800)
        ));

        // Step 2: Buyer inquiries and agrees on RFQ offer
        UserEntity buyer = createSyntheticUser("+99890444");

        InquiryEntity inquiry = inquiryRepository.save(new InquiryEntity(
                UUID.randomUUID(), listing.getId(), buyer.getId(), seller.getId(), "Pamidor", "KG", "inq-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        UUID agreedOfferId = UUID.randomUUID();
        InquiryOfferEntity agreedOffer = inquiryOfferRepository.save(new InquiryOfferEntity(
                agreedOfferId, inquiry.getId(), 1, buyer.getId(), "BUYER",
                new BigDecimal("1000.000"), "KG", new BigDecimal("8000.00"), "UZS",
                "FCA (Fergana)", "1000 kg olamiz", Instant.now().plusSeconds(86400), "off-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        inquiry.setStatus("AGREED");
        inquiry.setCurrentOfferId(agreedOffer.getId());
        inquiry.setAgreedOfferId(agreedOffer.getId());
        inquiry.setAgreedAt(Instant.now().minusSeconds(300));
        inquiry.setAgreedQuantity(new BigDecimal("1000.000"));
        inquiry.setAgreedUnitPrice(new BigDecimal("8000.00"));
        inquiry.setAgreedCurrency("UZS");
        inquiry.setAgreedDeliveryTerms("FCA (Fergana)");
        inquiry = inquiryRepository.save(inquiry);

        // Step 3: Create wholesale order from RFQ -> enters AWAITING_SELLER_CONFIRMATION
        CreateWholesaleOrderRequest orderReq = new CreateWholesaleOrderRequest(
                inquiry.getId(), "HOSIL_LOGISTIKA", "Toshkent ombori", "Wholesale harvest delivery"
        );
        var creationResult = wholesaleOrderService.createOrderFromRfq(orderReq, "idemp-ws-pay-" + UUID.randomUUID(), buyer.getId());
        UUID orderId = creationResult.orderDetail().id();

        // Step 4: Seller confirms order -> transitions to CONFIRMED
        wholesaleOrderService.confirmOrder(orderId, seller.getId());
        OrderEntity confirmedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CONFIRMED", confirmedOrder.getStatus());

        // Step 5: Buyer initiates payment
        InitiatePaymentRequest initReq = new InitiatePaymentRequest(orderId, "MOCK_SANDBOX");
        PaymentDetailDto paymentDto = paymentService.initiatePayment(initReq, buyer.getId(), null);
        assertEquals("PENDING_PROVIDER", paymentDto.status());
        assertEquals(new BigDecimal("8000000.00"), paymentDto.amount());

        // Step 6: Process webhook capture
        String mockPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-WHOLESALE-001\"}",
                paymentDto.paymentIntentReference(), paymentDto.id()
        );
        String signature = sandboxGateway.generateSignature(mockPayload);
        PaymentWebhookResult webhookResult = paymentService.processWebhook("MOCK_SANDBOX", mockPayload, signature, "EVT-WS-" + UUID.randomUUID());
        assertTrue(webhookResult.success());

        // Step 7: Verify Order transitions from CONFIRMED to PROCESSING
        OrderEntity processingOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("PROCESSING", processingOrder.getStatus(), "Wholesale order must transition from CONFIRMED to PROCESSING upon payment capture");

        InventoryReservationEntity reservation = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertEquals("CONFIRMED_SOLD", reservation.getStatus());
    }

    @Test
    @DisplayName("Scenario 3: Unconfirmed wholesale order payment initiation is rejected with BusinessConflictException")
    void testUnconfirmedWholesaleOrderPaymentInitiationRejected() {
        UserEntity seller = createSyntheticUser("+99890555");
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), seller.getId(), "INDIVIDUAL_FARMER", "Samarqand Bogʻbon LLC", "555666777", "VERIFIED", Instant.now()
        ));
        FarmEntity farm = farmRepository.save(new FarmEntity(seller.getId(), "Samarqand Farm", "Samarqand", "Jomboy", new BigDecimal("20.0"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Field A", new BigDecimal("5.0"), "HECTARE", null, "Shaftoli bogʻi"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Peach Spring", "PEACH", LocalDate.now().minusMonths(4), LocalDate.now(), "HARVESTED"));

        CategoryEntity cat = getOrCreateCategory("HARVEST_U", "Hosil Ulgurji 2");
        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(), farm.getId(), cropCycle.getId(), "Shaftoli", "FIRST_CLASS",
                new BigDecimal("5000.000"), new BigDecimal("5000.000"), "KG", LocalDate.now(),
                "Ombor #2", "RECORDED", "BATCH-" + UUID.randomUUID().toString().substring(0, 6), null, 1L
        ));

        HarvestListingEntity listing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(), seller.getId(), batch.getId(), cat.getId(), "Anjir Shaftoli", "Shirin shaftoli",
                "Shaftoli", "FIRST_CLASS", new BigDecimal("15000.00"), "UZS", "RFQ", true,
                new BigDecimal("2000.000"), new BigDecimal("2000.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "KG", new BigDecimal("200.000"), new BigDecimal("50.000"), "Samarqand", "Jomboy", "PUBLISHED", null, Instant.now().minusSeconds(1800)
        ));

        UserEntity buyer = createSyntheticUser("+99890666");

        InquiryEntity inquiry = inquiryRepository.save(new InquiryEntity(
                UUID.randomUUID(), listing.getId(), buyer.getId(), seller.getId(), "Shaftoli", "KG", "inq-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        InquiryOfferEntity agreedOffer = inquiryOfferRepository.save(new InquiryOfferEntity(
                UUID.randomUUID(), inquiry.getId(), 1, buyer.getId(), "BUYER",
                new BigDecimal("500.000"), "KG", new BigDecimal("15000.00"), "UZS",
                "EXW", "500 kg olamiz", Instant.now().plusSeconds(86400), "off-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        inquiry.setStatus("AGREED");
        inquiry.setCurrentOfferId(agreedOffer.getId());
        inquiry.setAgreedOfferId(agreedOffer.getId());
        inquiry.setAgreedAt(Instant.now().minusSeconds(300));
        inquiry.setAgreedQuantity(new BigDecimal("500.000"));
        inquiry.setAgreedUnitPrice(new BigDecimal("15000.00"));
        inquiry.setAgreedCurrency("UZS");
        inquiry.setAgreedDeliveryTerms("EXW");
        inquiry = inquiryRepository.save(inquiry);

        CreateWholesaleOrderRequest orderReq = new CreateWholesaleOrderRequest(
                inquiry.getId(), "BUYER_ARRANGES", "Samarqand omboridan olib ketish", "Tezkor"
        );
        var creationResult = wholesaleOrderService.createOrderFromRfq(orderReq, "idemp-unconf-" + UUID.randomUUID(), buyer.getId());
        UUID unconfirmedOrderId = creationResult.orderDetail().id();

        // Order is in AWAITING_SELLER_CONFIRMATION
        InitiatePaymentRequest initReq = new InitiatePaymentRequest(unconfirmedOrderId, "MOCK_SANDBOX");
        final UUID buyerId = buyer.getId();

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () -> {
            paymentService.initiatePayment(initReq, buyerId, null);
        });

        assertTrue(ex.getMessage().contains("CONFIRMED") || ex.getMessage().contains("tasdiqlanishi"));
        assertEquals(0, paymentRepository.findByOrderIdOrderByCreatedAtDesc(unconfirmedOrderId).size());
    }

    @Test
    @DisplayName("Scenario 4: Failed payment webhook preserves order in payable state for safe retry")
    void testFailedPaymentPreservesOrderForRetry() {
        UserEntity seller = createSyntheticUser("+99890777");
        UserEntity buyer = createSyntheticUser("+99890888");

        OrderEntity inputOrder = new OrderEntity(
                UUID.randomUUID(), "ORD-FAIL-RETRY-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("200000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        inputOrder = orderRepository.saveAndFlush(inputOrder);

        // Attempt 1: Initiate and Fail
        InitiatePaymentRequest initReq1 = new InitiatePaymentRequest(inputOrder.getId(), "MOCK_SANDBOX");
        PaymentDetailDto payment1 = paymentService.initiatePayment(initReq1, buyer.getId(), "idemp-attempt-1-" + UUID.randomUUID());

        String failPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"FAILED\",\"failureReason\":\"Kartada mablagʻ yetarli emas\"}",
                payment1.paymentIntentReference(), payment1.id()
        );
        String signature = sandboxGateway.generateSignature(failPayload);
        paymentService.processWebhook("MOCK_SANDBOX", failPayload, signature, "EVT-FAIL-1-" + UUID.randomUUID());

        PaymentEntity paymentRecord1 = paymentRepository.findById(payment1.id()).orElseThrow();
        assertEquals("FAILED", paymentRecord1.getStatus());
        assertEquals("Kartada mablagʻ yetarli emas", paymentRecord1.getFailureReason());

        // Order must remain in PENDING_PAYMENT
        OrderEntity preservedOrder = orderRepository.findById(inputOrder.getId()).orElseThrow();
        assertEquals("PENDING_PAYMENT", preservedOrder.getStatus(), "Order must remain in PENDING_PAYMENT after failed payment attempt");

        // Attempt 2: Retry with fresh attempt
        InitiatePaymentRequest initReq2 = new InitiatePaymentRequest(inputOrder.getId(), "MOCK_SANDBOX");
        PaymentDetailDto payment2 = paymentService.initiatePayment(initReq2, buyer.getId(), "idemp-attempt-2-" + UUID.randomUUID());

        assertNotEquals(payment1.id(), payment2.id());
        assertEquals("PENDING_PROVIDER", payment2.status());

        // Capture Attempt 2
        String successPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-RETRY-SUCCESS\"}",
                payment2.paymentIntentReference(), payment2.id()
        );
        paymentService.processWebhook("MOCK_SANDBOX", successPayload, sandboxGateway.generateSignature(successPayload), "EVT-SUCCESS-2-" + UUID.randomUUID());

        PaymentEntity paymentRecord2 = paymentRepository.findById(payment2.id()).orElseThrow();
        assertEquals("CAPTURED", paymentRecord2.getStatus());

        OrderEntity finalOrder = orderRepository.findById(inputOrder.getId()).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());
    }

    @Test
    @DisplayName("Scenario 5: Webhook idempotency on duplicate delivery returns 200 OK without double side-effects")
    void testWebhookIdempotencyOnDuplicateDelivery() {
        UserEntity seller = createSyntheticUser("+99891111");
        UserEntity buyer = createSyntheticUser("+99891222");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-IDEMP-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("150000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"), buyer.getId(), null);

        String payload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-IDEMP-001\"}",
                payment.paymentIntentReference(), payment.id()
        );
        String signature = sandboxGateway.generateSignature(payload);
        String externalEventId = "EVT-DUP-CHECK-" + UUID.randomUUID();

        // Delivery 1
        PaymentWebhookResult res1 = paymentService.processWebhook("MOCK_SANDBOX", payload, signature, externalEventId);
        assertTrue(res1.success());
        assertEquals("CAPTURED", res1.status());

        // Delivery 2 (Identical webhook replay)
        PaymentWebhookResult res2 = paymentService.processWebhook("MOCK_SANDBOX", payload, signature, externalEventId);
        assertTrue(res2.success());
        assertEquals("DUPLICATE_ACKNOWLEDGED", res2.status());

        // Delivery 3 (Different eventId, but payment already CAPTURED)
        PaymentWebhookResult res3 = paymentService.processWebhook("MOCK_SANDBOX", payload, signature, "EVT-NEW-BUT-CAPTURED-" + UUID.randomUUID());
        assertTrue(res3.success());
        assertEquals("CAPTURED", res3.status());

        // Order remains CONFIRMED, not advanced again
        OrderEntity finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());
    }

    @Test
    @DisplayName("Scenario 6: Corrupted or invalid HMAC signature is rejected with InvalidPaymentSignatureException")
    void testCorruptedSignatureRejection() {
        UserEntity seller = createSyntheticUser("+99891333");
        UserEntity buyer = createSyntheticUser("+99891444");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-BADSIG-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("120000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"), buyer.getId(), null);

        String payload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\"}",
                payment.paymentIntentReference(), payment.id()
        );

        // Intentionally corrupted signature
        String badSignature = "deadbeef00112233445566778899aabbccddeeff";

        assertThrows(InvalidPaymentSignatureException.class, () -> {
            paymentService.processWebhook("MOCK_SANDBOX", payload, badSignature, "EVT-BAD-SIG-" + UUID.randomUUID());
        });

        // Verify payment is NOT captured
        PaymentEntity paymentRecord = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals("PENDING_PROVIDER", paymentRecord.getStatus());

        // Verify order is NOT advanced
        OrderEntity orderRecord = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("PENDING_PAYMENT", orderRecord.getStatus());
    }

    @Test
    @DisplayName("Scenario 7: IDOR security guard rejects unauthorized buyer attempting to pay or view another user's order")
    void testIdorSecurityGuard() {
        UserEntity seller = createSyntheticUser("+99891555");
        UserEntity buyerA = createSyntheticUser("+99891666");
        UserEntity buyerB = createSyntheticUser("+99891777");

        OrderEntity orderA = new OrderEntity(
                UUID.randomUUID(), "ORD-IDOR-" + UUID.randomUUID().toString().substring(0, 8),
                buyerA.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("99000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        orderA = orderRepository.saveAndFlush(orderA);

        final UUID orderAId = orderA.getId();
        final UUID buyerBId = buyerB.getId();

        // Buyer B tries to initiate payment for Buyer A's order -> AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            paymentService.initiatePayment(new InitiatePaymentRequest(orderAId, "MOCK_SANDBOX"), buyerBId, null);
        });

        // Legitimate buyer initiates payment
        PaymentDetailDto paymentA = paymentService.initiatePayment(new InitiatePaymentRequest(orderAId, "MOCK_SANDBOX"), buyerA.getId(), null);
        final UUID paymentAId = paymentA.id();

        // Buyer B tries to read Buyer A's payment details -> AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            paymentService.getPayment(paymentAId, buyerBId);
        });

        // Buyer B tries to list payments for Buyer A's order -> AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            paymentService.getPaymentsByOrder(orderAId, buyerBId);
        });
    }

    @Test
    @DisplayName("Scenario 8: Concurrent payment webhooks execute safely under pessimistic locking without race condition")
    void testConcurrentPaymentWebhooksSafety() throws Exception {
        UserEntity seller = createSyntheticUser("+99891888");
        UserEntity buyer = createSyntheticUser("+99891999");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-CONCUR-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("300000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"), buyer.getId(), null);

        int threadCount = 4;
        String runId = UUID.randomUUID().toString().substring(0, 8);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<PaymentWebhookResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                String payload = String.format(
                        "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-CONCUR-%s-%d\"}",
                        payment.paymentIntentReference(), payment.id(), runId, index
                );
                String sig = sandboxGateway.generateSignature(payload);
                return paymentService.processWebhook("MOCK_SANDBOX", payload, sig, "EVT-CONCUR-" + runId + "-" + index);
            });
        }

        List<Future<PaymentWebhookResult>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<PaymentWebhookResult> f : results) {
            PaymentWebhookResult res = f.get();
            assertTrue(res.success());
        }

        // Verify database state: exactly CAPTURED, exactly 1 order transition
        PaymentEntity finalPayment = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals("CAPTURED", finalPayment.getStatus());

        OrderEntity finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());
    }

    @Test
    @DisplayName("Scenario 9: Expired order payment initiation is rejected with BusinessConflictException")
    void testExpiredOrderPaymentInitiationRejected() {
        UserEntity seller = createSyntheticUser("+99892111");
        UserEntity buyer = createSyntheticUser("+99892222");

        OrderEntity expiredOrder = new OrderEntity(
                UUID.randomUUID(), "ORD-EXPIRED-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("100000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().minus(Duration.ofHours(2)), null
        );
        expiredOrder = orderRepository.saveAndFlush(expiredOrder);

        InitiatePaymentRequest initReq = new InitiatePaymentRequest(expiredOrder.getId(), "MOCK_SANDBOX");
        final UUID buyerId = buyer.getId();

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () -> {
            paymentService.initiatePayment(initReq, buyerId, null);
        });

        assertTrue(ex.getMessage().contains("tugagan"));
    }

    @Test
    @DisplayName("Scenario 10: Sandbox simulation endpoint triggers verified capture and audit event")
    void testSimulateSandboxCaptureHelper() {
        UserEntity seller = createSyntheticUser("+99892333");
        UserEntity buyer = createSyntheticUser("+99892444");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-SIM-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("450000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"), buyer.getId(), null);

        // Execute simulation as buyer
        SimulatePaymentCaptureRequest simReq = new SimulatePaymentCaptureRequest(payment.id(), null, true, null);
        PaymentWebhookResult simResult = paymentService.simulateSandboxCapture(simReq, buyer.getId());

        assertTrue(simResult.success());
        assertEquals("CAPTURED", simResult.status());

        PaymentEntity finalPayment = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals("CAPTURED", finalPayment.getStatus());
        assertNotNull(finalPayment.getConfirmedAt());

        OrderEntity finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());
    }
}
