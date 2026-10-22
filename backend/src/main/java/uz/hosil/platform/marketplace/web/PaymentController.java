package uz.hosil.platform.marketplace.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.marketplace.service.PaymentService;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing provider-neutral payment initiation, status inquiry,
 * secure webhook processing, and sandbox capture endpoints (PAY-001, PAY-002, PAY-004).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Buyer initiates a payment attempt for a payable order (PAY-001).
     */
    @PostMapping("/initiate")
    public ResponseEntity<PaymentDetailDto> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentDetailDto payment = paymentService.initiatePayment(request, principal.userId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * Retrieves payment attempt details by payment ID (PAY-001).
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailDto> getPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentDetailDto payment = paymentService.getPayment(paymentId, principal.userId());
        return ResponseEntity.ok(payment);
    }

    /**
     * Retrieves all payment attempts for a given order (PAY-001).
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentDetailDto>> getPaymentsByOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PaymentDetailDto> payments = paymentService.getPaymentsByOrder(orderId, principal.userId());
        return ResponseEntity.ok(payments);
    }

    /**
     * Reconciles remote payment status with provider to handle timeouts without duplicate charges (PAY-004).
     */
    @PostMapping("/{paymentId}/reconcile")
    public ResponseEntity<PaymentDetailDto> reconcilePayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PaymentDetailDto payment = paymentService.reconcilePaymentStatus(paymentId, principal.userId());
        return ResponseEntity.ok(payment);
    }

    /**
     * Cancels an active pending payment attempt (PAY-001).
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentDetailDto> cancelPayment(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) CancelPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.reason() : null;
        PaymentDetailDto payment = paymentService.cancelPayment(paymentId, principal.userId(), reason);
        return ResponseEntity.ok(payment);
    }

    /**
     * Inbound payment gateway webhook handler with cryptographic HMAC verification (PAY-004).
     */
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<PaymentWebhookResult> handleProviderWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Signature", required = false) String signatureHeader,
            @RequestHeader(name = "X-Event-ID", required = false) String externalEventId
    ) {
        PaymentWebhookResult result = paymentService.processWebhook(provider, rawPayload, signatureHeader, externalEventId);
        return ResponseEntity.ok(result);
    }

    /**
     * Sandbox mock payment capture simulation endpoint (for local testing, CI, and mobile app demo).
     */
    @PostMapping("/mock-sandbox/simulate-capture")
    public ResponseEntity<PaymentWebhookResult> simulateSandboxCapture(
            @RequestBody SimulatePaymentCaptureRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : null;
        PaymentWebhookResult result = paymentService.simulateSandboxCapture(request, userId);
        return ResponseEntity.ok(result);
    }
}
