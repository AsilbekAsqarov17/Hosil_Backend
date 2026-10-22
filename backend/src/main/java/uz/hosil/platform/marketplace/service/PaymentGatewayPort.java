package uz.hosil.platform.marketplace.service;

import uz.hosil.platform.marketplace.domain.PaymentEntity;

/**
 * Provider-neutral gateway port abstraction for payment processing (PAY-001, DEC-007).
 */
public interface PaymentGatewayPort {

    /**
     * Provider identifier (e.g. 'MOCK_SANDBOX', 'PAYME', 'CLICK').
     */
    String getProviderName();

    /**
     * Initiates remote or sandbox payment attempt, attaching external references or URLs.
     */
    void initiatePayment(PaymentEntity payment);

    /**
     * Verifies cryptographic signature of incoming webhook callback using constant-time check.
     */
    boolean verifySignature(String rawPayload, String signatureHeader);

    /**
     * Utility method to generate valid HMAC signature for given payload (used in tests/simulations).
     */
    String generateSignature(String payload);

    /**
     * Queries provider API to reconcile remote payment status without charging again (PAY-004).
     */
    PaymentStatusResult queryPaymentStatus(PaymentEntity payment);
}
