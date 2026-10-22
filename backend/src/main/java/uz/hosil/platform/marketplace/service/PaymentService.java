package uz.hosil.platform.marketplace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.InvalidPaymentSignatureException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;
import uz.hosil.platform.marketplace.domain.PaymentEntity;
import uz.hosil.platform.marketplace.domain.PaymentEventEntity;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.PaymentEventRepository;
import uz.hosil.platform.marketplace.repository.PaymentRepository;
import uz.hosil.platform.marketplace.web.InitiatePaymentRequest;
import uz.hosil.platform.marketplace.web.PaymentDetailDto;
import uz.hosil.platform.marketplace.web.PaymentWebhookResult;
import uz.hosil.platform.marketplace.web.SimulatePaymentCaptureRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing marketplace payment transactions, provider-neutral lifecycle,
 * cryptographic HMAC verification, and transactional order/inventory reconciliation (PAY-001, PAY-002, PAY-004, DEC-007).
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final Map<String, PaymentGatewayPort> gatewayPorts;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final boolean simulationEnabled;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            InventoryReservationRepository inventoryReservationRepository,
            List<PaymentGatewayPort> gatewayPortList,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${hosil.payment.sandbox.simulation-enabled:true}") boolean simulationEnabled
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.gatewayPorts = new HashMap<>();
        for (PaymentGatewayPort port : gatewayPortList) {
            this.gatewayPorts.put(port.getProviderName().toUpperCase(), port);
        }
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.simulationEnabled = simulationEnabled;
    }

    /**
     * Initiates a payment attempt for a payable order (PAY-001, PAY-002).
     */
    public PaymentDetailDto initiatePayment(
            InitiatePaymentRequest request,
            UUID buyerUserId,
            String idempotencyKey
    ) {
        log.info("Initiating payment: orderId={}, buyer={}, provider={}, idempotencyKey={}",
                request.orderId(), buyerUserId, request.providerName(), idempotencyKey);

        // Step 1: Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentEntity> existing = paymentRepository.findByBuyerUserIdAndIdempotencyKey(
                    buyerUserId, idempotencyKey.trim()
            );
            if (existing.isPresent()) {
                PaymentEntity existingPayment = existing.get();
                if (!existingPayment.getOrderId().equals(request.orderId())) {
                    throw new BusinessConflictException("Idempotency-Key boshqa buyurtma bilan bogʻlangan");
                }
                log.info("Idempotent hit for payment initiation: paymentId={}, intentRef={}",
                        existingPayment.getId(), existingPayment.getPaymentIntentReference());
                return PaymentDetailDto.fromEntity(existingPayment);
            }
        }

        // Step 2: Fetch and Validate Order
        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + request.orderId()));

        // Step 3: IDOR Security Guard - Only order buyer can initiate payment
        if (!order.getBuyerUserId().equals(buyerUserId)) {
            log.warn("IDOR attempt blocked: user {} attempted to initiate payment for order {} owned by buyer {}",
                    buyerUserId, order.getId(), order.getBuyerUserId());
            throw new AccessDeniedException("Faqat buyurtma bergan xaridor toʻlovni amalga oshirishi mumkin");
        }

        // Step 4: Validate Order Status is in Payable State
        validateOrderPayableState(order);

        // Step 5: Check Payment Window Expiry
        if (order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Buyurtma uchun toʻlov muddati tugagan");
        }

        // Step 6: Verify No Active Captured Payment Exists
        Optional<PaymentEntity> capturedPayment = paymentRepository.findFirstByOrderIdAndStatus(order.getId(), "CAPTURED");
        if (capturedPayment.isPresent()) {
            throw new BusinessConflictException("Ushbu buyurtma uchun toʻlov allaqachon muvaffaqiyatli qabul qilingan");
        }

        // Step 7: Resolve Gateway Port
        String providerKey = request.providerName().trim().toUpperCase();
        PaymentGatewayPort gateway = gatewayPorts.get(providerKey);
        if (gateway == null) {
            log.warn("Unknown payment provider '{}', falling back to MOCK_SANDBOX", providerKey);
            gateway = gatewayPorts.get(MockSandboxPaymentGateway.PROVIDER_NAME);
            if (gateway == null) {
                throw new BusinessConflictException("Toʻlov tizimi qoʻllab-quvvatlanmaydi: " + providerKey);
            }
        }

        // Step 8: Build and Persist Payment Attempt
        UUID paymentId = UUID.randomUUID();
        String intentRef = "PI-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        BigDecimal amount = order.getTotalAmount();
        String currency = order.getCurrency();

        PaymentEntity payment = new PaymentEntity(
                paymentId,
                order.getId(),
                buyerUserId,
                intentRef,
                gateway.getProviderName(),
                amount,
                currency,
                "INITIATED",
                idempotencyKey != null ? idempotencyKey.trim() : null,
                order.getPaymentExpiresAt()
        );

        // Step 9: Invoke Gateway Adapter
        gateway.initiatePayment(payment);
        payment = paymentRepository.save(payment);

        // Step 10: Append Initial Audit Event
        PaymentEventEntity event = new PaymentEventEntity(
                UUID.randomUUID(),
                payment.getId(),
                "INTENT_INITIATED",
                null,
                payment.getStatus(),
                "{\"intentReference\":\"" + intentRef + "\",\"provider\":\"" + gateway.getProviderName() + "\"}",
                null,
                false
        );
        paymentEventRepository.save(event);

        log.info("Payment initiated successfully: paymentId={}, intentRef={}, status={}, amount={} {}",
                payment.getId(), intentRef, payment.getStatus(), amount, currency);

        return PaymentDetailDto.fromEntity(payment);
    }

    /**
     * Processes inbound webhook from a payment gateway with cryptographic HMAC verification and idempotency (PAY-004).
     */
    public PaymentWebhookResult processWebhook(
            String providerName,
            String rawPayload,
            String signatureHeader,
            String externalEventId
    ) {
        log.info("Processing payment webhook: provider={}, externalEventId={}", providerName, externalEventId);

        // Step 1: Resolve Gateway Adapter
        String providerKey = providerName != null ? providerName.trim().toUpperCase() : MockSandboxPaymentGateway.PROVIDER_NAME;
        PaymentGatewayPort gateway = gatewayPorts.get(providerKey);
        if (gateway == null) {
            gateway = gatewayPorts.get(MockSandboxPaymentGateway.PROVIDER_NAME);
            if (gateway == null) {
                throw new BusinessConflictException("Nomaʼlum toʻlov provayderi: " + providerName);
            }
        }

        // Step 2: Webhook Idempotency Check (PAY-004)
        if (externalEventId != null && !externalEventId.isBlank()) {
            boolean alreadyProcessed = paymentEventRepository.existsByExternalEventId(externalEventId.trim());
            if (alreadyProcessed) {
                log.info("Duplicate payment webhook detected by externalEventId: {}, acknowledging with 200 OK", externalEventId);
                return new PaymentWebhookResult(true, null, "DUPLICATE_ACKNOWLEDGED", "Webhook allaqachon qayta ishlangan");
            }
        }

        // Step 3: Cryptographic Signature Verification
        boolean isValidSignature = gateway.verifySignature(rawPayload, signatureHeader);
        if (!isValidSignature) {
            log.warn("Payment webhook signature verification failed for provider: {}", providerKey);
            throw new InvalidPaymentSignatureException("Toʻlov imzosi notoʻgʻri yoki buzilgan");
        }

        // Step 4: Parse Webhook Payload
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(rawPayload);
        } catch (Exception ex) {
            log.error("Failed to parse payment webhook payload JSON", ex);
            throw new BusinessConflictException("Yaroqsiz webhook JSON formati");
        }

        String paymentIntentReference = jsonNode.path("paymentIntentReference").asText(null);
        String paymentIdStr = jsonNode.path("paymentId").asText(null);
        String status = jsonNode.path("status").asText("CAPTURED").toUpperCase();
        String providerTxId = jsonNode.path("providerTransactionId").asText(null);
        String failureReason = jsonNode.path("failureReason").asText(null);

        // Step 5: Acquire Pessimistic Lock on Payment Record
        PaymentEntity payment;
        if (paymentIntentReference != null && !paymentIntentReference.isBlank()) {
            payment = paymentRepository.findByPaymentIntentReferenceForUpdate(paymentIntentReference.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Toʻlov tranzaksiyasi topilmadi: " + paymentIntentReference));
        } else if (paymentIdStr != null && !paymentIdStr.isBlank()) {
            payment = paymentRepository.findByIdForUpdate(UUID.fromString(paymentIdStr.trim()))
                    .orElseThrow(() -> new ResourceNotFoundException("Toʻlov tranzaksiyasi topilmadi: " + paymentIdStr));
        } else {
            throw new BusinessConflictException("Webhook payloadida paymentIntentReference yoki paymentId mavjud emas");
        }

        // Step 6: Idempotent Check on Payment State
        if ("CAPTURED".equals(payment.getStatus())) {
            log.info("Payment {} is already in terminal CAPTURED status, returning idempotent confirmation", payment.getId());
            return new PaymentWebhookResult(true, payment.getId(), payment.getStatus(), "Toʻlov allaqachon muvaffaqiyatli qabul qilingan");
        }

        String priorPaymentStatus = payment.getStatus();

        // Step 7: Handle Terminal Capture Transition
        if ("CAPTURED".equals(status) || "SUCCESS".equals(status)) {
            payment.setStatus("CAPTURED");
            payment.setConfirmedAt(Instant.now());
            if (providerTxId != null && !providerTxId.isBlank()) {
                payment.setProviderTransactionId(providerTxId.trim());
            }
            paymentRepository.save(payment);

            // Level 2: Reconcile Associated Order Status
            OrderEntity order = orderRepository.findByIdForUpdate(payment.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + payment.getOrderId()));

            String priorOrderStatus = order.getStatus();
            String newOrderStatus;

            if ("INPUT_DIRECT_CHECKOUT".equals(order.getOrderFlowType())) {
                // Input direct checkout: PENDING_PAYMENT -> CONFIRMED
                newOrderStatus = "CONFIRMED";
                order.setStatus(newOrderStatus);
                order.setConfirmedAt(Instant.now());
            } else {
                // Wholesale harvest order: CONFIRMED -> PROCESSING
                newOrderStatus = "PROCESSING";
                order.setStatus(newOrderStatus);
            }
            orderRepository.save(order);

            // Record Order Status Audit Trail
            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    priorOrderStatus,
                    newOrderStatus,
                    null,
                    "PAYMENT_GATEWAY",
                    String.format("Toʻlov tasdiqlandi (Provayder: %s, Ref: %s)",
                            payment.getProviderName(), payment.getPaymentIntentReference())
            );
            orderStatusHistoryRepository.save(history);

            // Confirm Active Inventory Reservation
            Optional<InventoryReservationEntity> reservationOpt = inventoryReservationRepository.findByOrderId(order.getId());
            reservationOpt.ifPresent(reservation -> {
                if ("ACTIVE".equals(reservation.getStatus())) {
                    reservation.setStatus("CONFIRMED_SOLD");
                    inventoryReservationRepository.save(reservation);
                    log.info("Confirmed inventory reservation {} for order {}", reservation.getId(), order.getId());
                }
            });

            // Append Payment Event Log with Sanitized Payload
            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_CAPTURED",
                    priorPaymentStatus,
                    "CAPTURED",
                    PaymentPayloadSanitizer.sanitize(rawPayload),
                    externalEventId,
                    true
            );
            paymentEventRepository.save(event);

            log.info("Payment captured successfully: paymentId={}, orderId={}, newOrderStatus={}",
                    payment.getId(), order.getId(), newOrderStatus);

            return new PaymentWebhookResult(true, payment.getId(), "CAPTURED", "Toʻlov muvaffaqiyatli qabul qilindi");

        } else if ("FAILED".equals(status)) {
            // Payment Failure: Mark payment failed, but leave order in payable state for retry
            payment.setStatus("FAILED");
            payment.setFailureReason(failureReason != null ? failureReason : "Toʻlov provayderi tomonidan rad etildi");
            paymentRepository.save(payment);

            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_FAILED",
                    priorPaymentStatus,
                    "FAILED",
                    PaymentPayloadSanitizer.sanitize(rawPayload),
                    externalEventId,
                    true
            );
            paymentEventRepository.save(event);

            log.info("Payment marked as FAILED: paymentId={}, reason={}", payment.getId(), payment.getFailureReason());
            return new PaymentWebhookResult(false, payment.getId(), "FAILED", "Toʻlov rad etildi");

        } else if ("CANCELLED".equals(status)) {
            // Payment Cancellation
            payment.setStatus("CANCELLED");
            payment.setFailureReason(failureReason != null ? failureReason : "Toʻlov bekor qilindi");
            paymentRepository.save(payment);

            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_CANCELLED",
                    priorPaymentStatus,
                    "CANCELLED",
                    PaymentPayloadSanitizer.sanitize(rawPayload),
                    externalEventId,
                    true
            );
            paymentEventRepository.save(event);

            log.info("Payment marked as CANCELLED: paymentId={}", payment.getId());
            return new PaymentWebhookResult(true, payment.getId(), "CANCELLED", "Toʻlov bekor qilindi");
        } else {
            throw new BusinessConflictException("Nomaʼlum toʻlov holati: " + status);
        }
    }

    private boolean isProductionEnvironment() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        return activeProfiles.contains("prod") || activeProfiles.contains("production");
    }

    /**
     * Helper to simulate sandbox payment capture for testing and mobile demo.
     * Strictly rejected in production profiles or when simulation is disabled.
     */
    public PaymentWebhookResult simulateSandboxCapture(
            SimulatePaymentCaptureRequest request,
            UUID requestingUserId
    ) {
        if (!simulationEnabled || isProductionEnvironment()) {
            log.warn("Blocked sandbox payment simulation: simulationEnabled={}, isProduction={}, requestingUser={}",
                    simulationEnabled, isProductionEnvironment(), requestingUserId);
            throw new BusinessConflictException("Toʻlov simulyatsiyasi ushbu muhitda oʻchirilgan");
        }

        PaymentEntity payment;
        if (request.paymentId() != null) {
            payment = paymentRepository.findById(request.paymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Toʻlov topilmadi: " + request.paymentId()));
        } else if (request.paymentIntentReference() != null) {
            payment = paymentRepository.findByPaymentIntentReference(request.paymentIntentReference())
                    .orElseThrow(() -> new ResourceNotFoundException("Toʻlov topilmadi: " + request.paymentIntentReference()));
        } else {
            throw new BusinessConflictException("paymentId yoki paymentIntentReference koʻrsatilishi shart");
        }

        // IDOR Check: Only the order buyer or seller or admin can trigger sandbox simulation
        if (requestingUserId != null && !payment.getBuyerUserId().equals(requestingUserId)) {
            OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order == null || !order.getSellerUserId().equals(requestingUserId)) {
                throw new AccessDeniedException("Simulyatsiyani faqat buyurtma ishtirokchisi amalga oshirishi mumkin");
            }
        }

        PaymentGatewayPort gateway = gatewayPorts.get(MockSandboxPaymentGateway.PROVIDER_NAME);
        if (gateway == null) {
            throw new IllegalStateException("Mock sandbox gateway topilmadi");
        }

        boolean isSuccess = Boolean.TRUE.equals(request.success());
        String status = isSuccess ? "CAPTURED" : "FAILED";
        String externalEventId = "SIM-EVT-" + UUID.randomUUID().toString().substring(0, 12);
        String mockPayload = String.format(
                "{\"paymentIntentReference\":\"%s\",\"paymentId\":\"%s\",\"status\":\"%s\",\"providerTransactionId\":\"%s\",\"failureReason\":%s}",
                payment.getPaymentIntentReference(),
                payment.getId(),
                status,
                "TX-SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.failureReason() != null ? "\"" + request.failureReason() + "\"" : "null"
        );

        String signature = gateway.generateSignature(mockPayload);
        return processWebhook(MockSandboxPaymentGateway.PROVIDER_NAME, mockPayload, signature, externalEventId);
    }

    /**
     * Reconciles remote payment status with provider without creating duplicate charges (PAY-004).
     */
    public PaymentDetailDto reconcilePaymentStatus(UUID paymentId, UUID requestingUserId) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Toʻlov topilmadi: " + paymentId));

        if (requestingUserId != null) {
            validateUserAccessToPayment(payment, requestingUserId);
        }

        if ("CAPTURED".equals(payment.getStatus())) {
            log.info("Reconciliation hit: payment {} is already in terminal CAPTURED state", payment.getId());
            return PaymentDetailDto.fromEntity(payment);
        }

        if ("CANCELLED".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
            log.info("Reconciliation hit: payment {} is already in terminal {} state", payment.getId(), payment.getStatus());
            return PaymentDetailDto.fromEntity(payment);
        }

        String providerKey = payment.getProviderName().toUpperCase();
        PaymentGatewayPort gateway = gatewayPorts.get(providerKey);
        if (gateway == null) {
            throw new BusinessConflictException("Nomaʼlum toʻlov provayderi: " + payment.getProviderName());
        }

        PaymentStatusResult statusResult = gateway.queryPaymentStatus(payment);
        log.info("Reconciled provider status for paymentId {}: status={}, txId={}",
                payment.getId(), statusResult.status(), statusResult.providerTransactionId());

        String priorStatus = payment.getStatus();

        if ("CAPTURED".equals(statusResult.status())) {
            payment.setStatus("CAPTURED");
            payment.setConfirmedAt(Instant.now());
            if (statusResult.providerTransactionId() != null && !statusResult.providerTransactionId().isBlank()) {
                payment.setProviderTransactionId(statusResult.providerTransactionId());
            }
            paymentRepository.save(payment);

            // Reconcile order status
            OrderEntity order = orderRepository.findByIdForUpdate(payment.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + payment.getOrderId()));

            String priorOrderStatus = order.getStatus();
            String newOrderStatus;
            if ("INPUT_DIRECT_CHECKOUT".equals(order.getOrderFlowType())) {
                newOrderStatus = "CONFIRMED";
                order.setStatus(newOrderStatus);
                order.setConfirmedAt(Instant.now());
            } else {
                newOrderStatus = "PROCESSING";
                order.setStatus(newOrderStatus);
            }
            orderRepository.save(order);

            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    priorOrderStatus,
                    newOrderStatus,
                    requestingUserId,
                    "PAYMENT_RECONCILIATION",
                    String.format("Toʻlov holati muvaffaqiyatli sinxronlashtirildi (Ref: %s)", payment.getPaymentIntentReference())
            );
            orderStatusHistoryRepository.save(history);

            inventoryReservationRepository.findByOrderId(order.getId()).ifPresent(reservation -> {
                if ("ACTIVE".equals(reservation.getStatus())) {
                    reservation.setStatus("CONFIRMED_SOLD");
                    inventoryReservationRepository.save(reservation);
                    log.info("Confirmed inventory reservation {} for order {}", reservation.getId(), order.getId());
                }
            });

            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_RECONCILED",
                    priorStatus,
                    "CAPTURED",
                    PaymentPayloadSanitizer.sanitize(String.format("{\"reconciliationResult\":\"CAPTURED\",\"providerTxId\":\"%s\"}", statusResult.providerTransactionId())),
                    "RECONCILE-" + UUID.randomUUID().toString().substring(0, 8),
                    true
            );
            paymentEventRepository.save(event);

        } else if ("FAILED".equals(statusResult.status())) {
            payment.setStatus("FAILED");
            payment.setFailureReason(statusResult.failureReason() != null ? statusResult.failureReason() : "Toʻlov muvaffaqiyatsiz yakunlangan");
            paymentRepository.save(payment);

            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_RECONCILED_FAILED",
                    priorStatus,
                    "FAILED",
                    PaymentPayloadSanitizer.sanitize(String.format("{\"reconciliationResult\":\"FAILED\",\"reason\":\"%s\"}", statusResult.failureReason())),
                    "RECONCILE-FAIL-" + UUID.randomUUID().toString().substring(0, 8),
                    true
            );
            paymentEventRepository.save(event);

        } else if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(Instant.now())) {
            payment.setStatus("EXPIRED");
            payment.setFailureReason("Toʻlov kutish muddati tugagan");
            paymentRepository.save(payment);

            PaymentEventEntity event = new PaymentEventEntity(
                    UUID.randomUUID(),
                    payment.getId(),
                    "PAYMENT_EXPIRED",
                    priorStatus,
                    "EXPIRED",
                    PaymentPayloadSanitizer.sanitize("{\"reason\":\"Payment window expired during reconciliation\"}"),
                    "EXPIRED-" + UUID.randomUUID().toString().substring(0, 8),
                    true
            );
            paymentEventRepository.save(event);
        }

        return PaymentDetailDto.fromEntity(payment);
    }

    /**
     * Cancels an active pending payment attempt (PAY-001).
     */
    public PaymentDetailDto cancelPayment(UUID paymentId, UUID requestingUserId, String reason) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Toʻlov topilmadi: " + paymentId));

        if (requestingUserId != null) {
            validateUserAccessToPayment(payment, requestingUserId);
        }

        if ("CAPTURED".equals(payment.getStatus())) {
            throw new BusinessConflictException("Muvaffaqiyatli toʻlangan toʻlovni bekor qilib boʻlmaydi");
        }

        if ("CANCELLED".equals(payment.getStatus())) {
            return PaymentDetailDto.fromEntity(payment);
        }

        String priorStatus = payment.getStatus();
        payment.setStatus("CANCELLED");
        String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Foydalanuvchi tomonidan bekor qilindi";
        payment.setFailureReason(effectiveReason);
        paymentRepository.save(payment);

        PaymentEventEntity event = new PaymentEventEntity(
                UUID.randomUUID(),
                payment.getId(),
                "PAYMENT_CANCELLED",
                priorStatus,
                "CANCELLED",
                PaymentPayloadSanitizer.sanitize(String.format("{\"reason\":\"%s\",\"cancelledBy\":\"%s\"}", effectiveReason, requestingUserId)),
                "CANCEL-" + UUID.randomUUID().toString().substring(0, 8),
                true
        );
        paymentEventRepository.save(event);
        log.info("Payment cancelled: paymentId={}, buyer={}, reason={}", payment.getId(), requestingUserId, effectiveReason);

        return PaymentDetailDto.fromEntity(payment);
    }

    /**
     * Diagnostic and monitoring sweep for stale pending payments.
     */
    public int reconcileStalePendingPayments(Duration staleThreshold) {
        Instant threshold = Instant.now().minus(staleThreshold);
        List<PaymentEntity> stalePayments = paymentRepository.findByStatusAndCreatedAtBefore("PENDING_PROVIDER", threshold);
        int reconciledCount = 0;
        for (PaymentEntity payment : stalePayments) {
            try {
                log.warn("[STALE_PAYMENT_ALERT] Discovered stale pending payment: paymentId={}, intentRef={}, ageMinutes={}",
                        payment.getId(), payment.getPaymentIntentReference(),
                        Duration.between(payment.getCreatedAt(), Instant.now()).toMinutes());
                reconcilePaymentStatus(payment.getId(), null);
                reconciledCount++;
            } catch (Exception ex) {
                log.error("Failed to reconcile stale payment: " + payment.getId(), ex);
            }
        }
        return reconciledCount;
    }

    /**
     * Retrieves payment details by ID with IDOR protection (PAY-001).
     */
    @Transactional(readOnly = true)
    public PaymentDetailDto getPayment(UUID paymentId, UUID requestingUserId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Toʻlov topilmadi: " + paymentId));

        validateUserAccessToPayment(payment, requestingUserId);
        return PaymentDetailDto.fromEntity(payment);
    }

    /**
     * Retrieves all payment attempts for a given order with IDOR protection.
     */
    @Transactional(readOnly = true)
    public List<PaymentDetailDto> getPaymentsByOrder(UUID orderId, UUID requestingUserId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!order.getBuyerUserId().equals(requestingUserId) && !order.getSellerUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("Ushbu buyurtma toʻlovlarini koʻrish huquqi mavjud emas");
        }

        List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        return payments.stream().map(PaymentDetailDto::fromEntity).toList();
    }

    private void validateOrderPayableState(OrderEntity order) {
        if ("INPUT_DIRECT_CHECKOUT".equals(order.getOrderFlowType())) {
            if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                throw new BusinessConflictException(
                        "Qishloq xoʻjaligi kirimlari buyurtmasi toʻlov kutilayotgan holatda emas (status=" + order.getStatus() + ")"
                );
            }
        } else if ("HARVEST_PURCHASE_REQUEST".equals(order.getOrderFlowType())) {
            if (!"CONFIRMED".equals(order.getStatus())) {
                throw new BusinessConflictException(
                        "Ulgurji hosil buyurtmasi toʻlov uchun avval sotuvchi tomonidan tasdiqlanishi (CONFIRMED) kerak (hozirgi status=" + order.getStatus() + ")"
                );
            }
        } else {
            throw new BusinessConflictException("Nomaʼlum buyurtma oqimi turi: " + order.getOrderFlowType());
        }
    }

    private void validateUserAccessToPayment(PaymentEntity payment, UUID requestingUserId) {
        if (payment.getBuyerUserId().equals(requestingUserId)) {
            return;
        }
        OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order != null && order.getSellerUserId().equals(requestingUserId)) {
            return;
        }
        throw new AccessDeniedException("Toʻlov tafsilotlariga kirish huquqi yetarli emas");
    }
}
