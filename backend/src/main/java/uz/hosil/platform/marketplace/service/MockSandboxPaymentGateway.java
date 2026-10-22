package uz.hosil.platform.marketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.hosil.platform.marketplace.domain.PaymentEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic sandbox mock payment gateway adapter with constant-time HMAC-SHA256 verification (PAY-001, PAY-004).
 */
@Component
public class MockSandboxPaymentGateway implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MockSandboxPaymentGateway.class);
    public static final String PROVIDER_NAME = "MOCK_SANDBOX";

    private final String secretKey;
    private final Map<String, PaymentStatusResult> simulatedProviderStates = new ConcurrentHashMap<>();

    public MockSandboxPaymentGateway(
            @Value("${hosil.payment.sandbox.secret-key:${hosil.payment.mock.secret-key:sandbox_secret_hosil_2026}}") String secretKey
    ) {
        this.secretKey = secretKey;
    }

    public void setSimulatedProviderState(String paymentIntentReference, PaymentStatusResult result) {
        simulatedProviderStates.put(paymentIntentReference, result);
    }

    public void clearSimulatedProviderStates() {
        simulatedProviderStates.clear();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void initiatePayment(PaymentEntity payment) {
        String mockTxId = "TX-SANDBOX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        payment.setProviderTransactionId(mockTxId);
        payment.setStatus("PENDING_PROVIDER");
        log.info("Initiated sandbox payment: intentRef={}, providerTxId={}, amount={} {}",
                payment.getPaymentIntentReference(), mockTxId, payment.getAmount(), payment.getCurrency());
    }

    @Override
    public boolean verifySignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || rawPayload == null) {
            log.warn("Payment webhook signature rejected: signature header or payload is blank");
            return false;
        }

        try {
            String expectedSignature = generateSignature(rawPayload);
            byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
            byte[] providedBytes = signatureHeader.trim().getBytes(StandardCharsets.UTF_8);

            boolean matches = MessageDigest.isEqual(expectedBytes, providedBytes);
            if (!matches) {
                log.warn("Payment webhook signature mismatch for sandbox provider");
            }
            return matches;
        } catch (Exception ex) {
            log.error("Error during webhook HMAC-SHA256 calculation", ex);
            return false;
        }
    }

    @Override
    public String generateSignature(String payload) {
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKeySpec);
            byte[] hash = hmacSha256.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", ex);
        }
    }

    @Override
    public PaymentStatusResult queryPaymentStatus(PaymentEntity payment) {
        if (payment == null) {
            return PaymentStatusResult.notFound();
        }

        String ref = payment.getPaymentIntentReference();
        if (ref != null && simulatedProviderStates.containsKey(ref)) {
            PaymentStatusResult simulated = simulatedProviderStates.get(ref);
            log.info("Returning simulated provider status for intentRef {}: status={}", ref, simulated.status());
            return simulated;
        }

        if ("CAPTURED".equals(payment.getStatus())) {
            return PaymentStatusResult.captured(payment.getProviderTransactionId());
        }
        if ("FAILED".equals(payment.getStatus())) {
            return PaymentStatusResult.failed(payment.getProviderTransactionId(), payment.getFailureReason());
        }
        return PaymentStatusResult.pending(payment.getProviderTransactionId());
    }
}
