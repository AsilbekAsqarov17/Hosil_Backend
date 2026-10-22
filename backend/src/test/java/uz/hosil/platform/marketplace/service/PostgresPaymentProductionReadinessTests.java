package uz.hosil.platform.marketplace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.PaymentEntity;
import uz.hosil.platform.marketplace.domain.PaymentEventEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.PaymentEventRepository;
import uz.hosil.platform.marketplace.repository.PaymentRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateInputOrderRequest;
import uz.hosil.platform.marketplace.web.InitiatePaymentRequest;
import uz.hosil.platform.marketplace.web.PaymentDetailDto;
import uz.hosil.platform.marketplace.web.PaymentWebhookResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 16 + Flyway backed integration tests validating Milestone 7:
 * Payment Production Readiness, startup configuration safety, timeout status reconciliation,
 * cancellation guards, sensitive payload sanitization, and concurrent execution under pessimistic locking.
 */
@SpringBootTest
@ActiveProfiles("local")
class PostgresPaymentProductionReadinessTests {

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
    private InventoryReservationRepository inventoryReservationRepository;

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

    private static final java.util.concurrent.atomic.AtomicLong PHONE_SEQ =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() % 700000000L + 100000000L);

    private UserEntity createSyntheticUser(String phonePrefix) {
        String phone = "+998" + PHONE_SEQ.incrementAndGet();
        UserEntity user = new UserEntity(phone, "ACTIVE", "uz");
        user = userRepository.save(user);

        UserProfileEntity profile = new UserProfileEntity(user.getId(), "Test User " + phone.substring(phone.length() - 4), "Toshkent", "Yunusobod");
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
    @DisplayName("Readiness 1: Startup configuration validator rejects insecure production configurations")
    void testProductionConfigurationStartupValidation() {
        // Test 1A: Rejects simulation enabled in prod profile
        MockEnvironment prodEnv1 = new MockEnvironment();
        prodEnv1.setActiveProfiles("prod");
        PaymentConfigValidator validator1 = new PaymentConfigValidator(
                prodEnv1, true, "a_very_strong_32_character_secret_key_123"
        );
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, validator1::validatePaymentConfig);
        assertTrue(ex1.getMessage().contains("simulation-enabled must be false in production profile"));

        // Test 1B: Rejects default dev sandbox secret in prod profile
        MockEnvironment prodEnv2 = new MockEnvironment();
        prodEnv2.setActiveProfiles("prod");
        PaymentConfigValidator validator2 = new PaymentConfigValidator(
                prodEnv2, false, "sandbox_secret_hosil_2026"
        );
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, validator2::validatePaymentConfig);
        assertTrue(ex2.getMessage().contains("must not use the insecure default development secret"));

        // Test 1C: Rejects short secret (< 32 chars) in prod profile
        MockEnvironment prodEnv3 = new MockEnvironment();
        prodEnv3.setActiveProfiles("prod");
        PaymentConfigValidator validator3 = new PaymentConfigValidator(
                prodEnv3, false, "short_secret_key"
        );
        IllegalStateException ex3 = assertThrows(IllegalStateException.class, validator3::validatePaymentConfig);
        assertTrue(ex3.getMessage().contains("must be at least 256 bits (32 characters)"));

        // Test 1D: Valid production configuration passes cleanly
        MockEnvironment prodEnv4 = new MockEnvironment();
        prodEnv4.setActiveProfiles("prod");
        PaymentConfigValidator validator4 = new PaymentConfigValidator(
                prodEnv4, false, "production_secret_key_exceeding_32_characters_hmac"
        );
        assertDoesNotThrow(validator4::validatePaymentConfig);
    }

    @Test
    @DisplayName("Readiness 2: Timeout reconciliation recovers captured status without creating duplicate charge")
    void testTimeoutReconciliationWithoutDuplicateCharge() {
        UserEntity seller = createSyntheticUser("+99890111");
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), seller.getId(), "COMMERCIAL_SUPPLIER", "Agro Dealer LLC", "123456789", "VERIFIED", Instant.now()
        ));

        CategoryEntity cat = getOrCreateCategory("FERT_M7_1", "Fertilizer M7");
        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Urea M7", "Urea M7 RU", "MAXAM", "NPK", "BAG_50KG", "Mineral fertilizer", true
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

        CreateInputOrderRequest.InputOrderItemRequest itemReq = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("100.000"), "BAG_50KG"
        );
        CreateInputOrderRequest orderReq = new CreateInputOrderRequest(
                List.of(itemReq), "BUYER_ARRANGES", "Toshkent shahar", null, "Test direct checkout input order"
        );
        var creationResult = inputOrderService.createInputOrder(orderReq, "idemp-input-m7-" + UUID.randomUUID(), buyer.getId());
        UUID orderId = creationResult.orderDetail().id();

        // Buyer initiates payment
        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(orderId, "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-pay-rec-" + UUID.randomUUID()
        );
        assertEquals("PENDING_PROVIDER", payment.status());

        // Simulate provider-side capture with network timeout during webhook dispatch
        String simulatedTxId = "TX-RECONCILE-" + UUID.randomUUID().toString().substring(0, 8);
        sandboxGateway.setSimulatedProviderState(
                payment.paymentIntentReference(),
                PaymentStatusResult.captured(simulatedTxId)
        );

        // Client or background reconciler queries remote status
        PaymentDetailDto reconciled = paymentService.reconcilePaymentStatus(payment.id(), buyer.getId());

        assertEquals("CAPTURED", reconciled.status());
        assertEquals(simulatedTxId, reconciled.providerTransactionId());

        // Verify database integrity: order is CONFIRMED, reservation is CONFIRMED_SOLD, no duplicate payments
        OrderEntity finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());
        assertNotNull(finalOrder.getConfirmedAt());

        InventoryReservationEntity reservation = inventoryReservationRepository.findByOrderId(orderId).orElseThrow();
        assertEquals("CONFIRMED_SOLD", reservation.getStatus());

        List<PaymentEntity> allPaymentsForOrder = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertEquals(1, allPaymentsForOrder.size(), "Reconciliation must not create duplicate payment rows");

        // Calling reconcile again is strictly idempotent
        PaymentDetailDto reconciledAgain = paymentService.reconcilePaymentStatus(payment.id(), buyer.getId());
        assertEquals("CAPTURED", reconciledAgain.status());

        sandboxGateway.clearSimulatedProviderStates();
    }

    @Test
    @DisplayName("Readiness 3: Timeout reconciliation marks payment FAILED when provider reports failure, leaving order payable")
    void testTimeoutReconciliationHandlesRemoteFailure() {
        UserEntity seller = createSyntheticUser("+99890333");
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), seller.getId(), "COMMERCIAL_SUPPLIER", "Agro Fail LLC", "123456789", "VERIFIED", Instant.now()
        ));

        CategoryEntity cat = getOrCreateCategory("FERT_M7_2", "Fertilizer M7 2");
        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Nitrate M7", "Nitrate M7 RU", "MAXAM", "NPK", "BAG_50KG", "Mineral fertilizer", true
        ));

        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), seller.getId(), product.getId(),
                new BigDecimal("6000.00"), "UZS", "BAG_50KG",
                new BigDecimal("500.000"), BigDecimal.ZERO,
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Yunusobod",
                "Omborda mavjud", "ACTIVE"
        ));

        UserEntity buyer = createSyntheticUser("+99890444");

        CreateInputOrderRequest.InputOrderItemRequest itemReq = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("10.000"), "BAG_50KG"
        );
        CreateInputOrderRequest orderReq = new CreateInputOrderRequest(
                List.of(itemReq), "BUYER_ARRANGES", "Toshkent shahar", null, "Test direct checkout input order"
        );
        var creationResult = inputOrderService.createInputOrder(orderReq, "idemp-fail-m7-" + UUID.randomUUID(), buyer.getId());
        UUID orderId = creationResult.orderDetail().id();

        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(orderId, "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-pay-fail-rec-" + UUID.randomUUID()
        );

        // Provider reports card declined
        sandboxGateway.setSimulatedProviderState(
                payment.paymentIntentReference(),
                PaymentStatusResult.failed("TX-DECLINED-001", "Karta hisobida yetarli mablagʻ mavjud emas")
        );

        PaymentDetailDto reconciled = paymentService.reconcilePaymentStatus(payment.id(), buyer.getId());
        assertEquals("FAILED", reconciled.status());
        assertTrue(reconciled.failureReason().contains("yetarli mablagʻ mavjud emas"));

        // Order remains in payable PENDING_PAYMENT state so buyer can retry
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("PENDING_PAYMENT", order.getStatus());

        InventoryReservationEntity reservation = inventoryReservationRepository.findByOrderId(orderId).orElseThrow();
        assertEquals("ACTIVE", reservation.getStatus());

        sandboxGateway.clearSimulatedProviderStates();
    }

    @Test
    @DisplayName("Readiness 4: Active pending payment cancellation transitions to CANCELLED and is blocked on CAPTURED payments")
    void testPaymentCancellationLifecycleAndGuards() {
        UserEntity seller = createSyntheticUser("+99890555");
        UserEntity buyer = createSyntheticUser("+99890666");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-CANCEL-TEST-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("500000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-cancel-pay-" + UUID.randomUUID()
        );
        assertEquals("PENDING_PROVIDER", payment.status());

        // Cancel pending payment
        PaymentDetailDto cancelled = paymentService.cancelPayment(payment.id(), buyer.getId(), "Xaridor toʻlov usulini oʻzgartirdi");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Xaridor toʻlov usulini oʻzgartirdi", cancelled.failureReason());

        // Idempotent cancel check
        PaymentDetailDto cancelledAgain = paymentService.cancelPayment(payment.id(), buyer.getId(), "Takroriy bekor qilish");
        assertEquals("CANCELLED", cancelledAgain.status());

        // Now test cancellation is strictly blocked on a CAPTURED payment
        PaymentEntity capturedPayment = new PaymentEntity(
                UUID.randomUUID(), order.getId(), buyer.getId(),
                "PAY-INTENT-CAPTURED-" + UUID.randomUUID(),
                "MOCK_SANDBOX",
                new BigDecimal("500000.00"), "UZS", "CAPTURED",
                "idemp-captured-" + UUID.randomUUID(),
                Instant.now().plus(Duration.ofHours(24))
        );
        capturedPayment.setConfirmedAt(Instant.now());
        capturedPayment = paymentRepository.saveAndFlush(capturedPayment);

        final UUID capturedId = capturedPayment.getId();
        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> paymentService.cancelPayment(capturedId, buyer.getId(), "Bekor qilish urinishi")
        );
        assertTrue(ex.getMessage().contains("Muvaffaqiyatli toʻlangan toʻlovni bekor qilib boʻlmaydi"));
    }

    @Test
    @DisplayName("Readiness 5: Sensitive financial and authentication data is masked in payment audit events")
    void testSensitiveDataSanitizationInPaymentEvents() {
        UserEntity seller = createSyntheticUser("+99890777");
        UserEntity buyer = createSyntheticUser("+99890888");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-SENSITIVE-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("120000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-sensitive-" + UUID.randomUUID()
        );

        // Webhook containing sensitive PAN, CVV, and token
        String rawWebhookPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\"," +
                "\"pan\":\"8600123456789012\",\"cvv\":\"123\",\"token\":\"secret_token_value_abc\"," +
                "\"providerTransactionId\":\"TX-SENSITIVE-001\"}",
                payment.paymentIntentReference(), payment.id()
        );
        String signature = sandboxGateway.generateSignature(rawWebhookPayload);
        String eventId = "EVT-SENSITIVE-" + UUID.randomUUID();

        PaymentWebhookResult result = paymentService.processWebhook("MOCK_SANDBOX", rawWebhookPayload, signature, eventId);
        assertTrue(result.success());

        // Check payment event stored in database
        PaymentEventEntity event = paymentEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getExternalEventId()))
                .findFirst()
                .orElseThrow();

        String snapshot = event.getPayloadSnapshot();
        assertNotNull(snapshot);
        assertFalse(snapshot.contains("8600123456789012"), "Plain credit card PAN must NOT be stored in event audit log");
        assertFalse(snapshot.contains("\"cvv\":\"123\""), "Plain CVV must NOT be stored in event audit log");
        assertFalse(snapshot.contains("secret_token_value_abc"), "Plain secret tokens must NOT be stored in event audit log");
        assertTrue(snapshot.contains("***9012") || snapshot.contains("****-****-****-9012"), "Masked PAN must be present");
    }

    @Test
    @DisplayName("Readiness 6: IDOR security guards prevent unauthorized payment access and reconciliation")
    void testIdorSecurityProtection() {
        UserEntity seller = createSyntheticUser("+99890999");
        UserEntity buyer = createSyntheticUser("+99891000");
        UserEntity attacker = createSyntheticUser("+99891111");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-IDOR-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("350000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-idor-" + UUID.randomUUID()
        );

        // Attacker attempts to inspect payment
        assertThrows(AccessDeniedException.class, () -> paymentService.getPayment(payment.id(), attacker.getId()));

        // Attacker attempts to cancel payment
        assertThrows(AccessDeniedException.class, () -> paymentService.cancelPayment(payment.id(), attacker.getId(), "Malicious cancel"));

        // Attacker attempts to reconcile payment
        assertThrows(AccessDeniedException.class, () -> paymentService.reconcilePaymentStatus(payment.id(), attacker.getId()));
    }

    @Test
    @DisplayName("Readiness 7: Stale payment detection identifies unresolved pending payments")
    void testStalePaymentDetectionAndReconciliation() {
        UserEntity seller = createSyntheticUser("+99891222");
        UserEntity buyer = createSyntheticUser("+99891333");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-STALE-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("80000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        paymentService.initiatePayment(
                new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-stale-" + UUID.randomUUID()
        );

        // Run sweep with 0 threshold (catches all pending payments created before now)
        int processed = paymentService.reconcileStalePendingPayments(Duration.ofSeconds(0));
        assertTrue(processed >= 1, "Stale payment reaper must detect and process pending payments");
    }

    @Test
    @DisplayName("Readiness 8: Concurrent reconciliation and webhook delivery executes safely under pessimistic locking")
    void testConcurrentReconciliationAndWebhookSafety() throws Exception {
        UserEntity seller = createSyntheticUser("+99891444");
        UserEntity buyer = createSyntheticUser("+99891555");

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), "ORD-RACE-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(), seller.getId(), "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("400000.00"), "UZS", "PENDING_PAYMENT",
                "BUYER_ARRANGES", "Toshkent", null, null, Instant.now().plus(Duration.ofHours(24)), null
        );
        order = orderRepository.saveAndFlush(order);

        PaymentDetailDto payment = paymentService.initiatePayment(
                new InitiatePaymentRequest(order.getId(), "MOCK_SANDBOX"),
                buyer.getId(),
                "idemp-race-" + UUID.randomUUID()
        );

        sandboxGateway.setSimulatedProviderState(
                payment.paymentIntentReference(),
                PaymentStatusResult.captured("TX-CONCUR-STATUS-01")
        );

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        // 2 threads dispatch webhooks, 2 threads invoke reconciliation
        for (int i = 0; i < 2; i++) {
            final int index = i;
            tasks.add(() -> {
                String payload = String.format(
                        "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"CAPTURED\",\"providerTransactionId\":\"TX-WEBHOOK-%d\"}",
                        payment.paymentIntentReference(), payment.id(), index
                );
                String sig = sandboxGateway.generateSignature(payload);
                PaymentWebhookResult res = paymentService.processWebhook("MOCK_SANDBOX", payload, sig, "EVT-RACE-" + UUID.randomUUID());
                return res.success();
            });
        }

        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                PaymentDetailDto res = paymentService.reconcilePaymentStatus(payment.id(), buyer.getId());
                return "CAPTURED".equals(res.status());
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> f : results) {
            assertTrue(f.get());
        }

        PaymentEntity finalPayment = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals("CAPTURED", finalPayment.getStatus());

        OrderEntity finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("CONFIRMED", finalOrder.getStatus());

        sandboxGateway.clearSimulatedProviderStates();
    }
}
