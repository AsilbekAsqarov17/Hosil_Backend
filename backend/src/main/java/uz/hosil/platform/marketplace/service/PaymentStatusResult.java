package uz.hosil.platform.marketplace.service;

/**
 * Result DTO representing provider status inquiry response (PAY-001, PAY-004).
 */
public record PaymentStatusResult(
        boolean success,
        String status,
        String providerTransactionId,
        String failureReason
) {
    public static PaymentStatusResult captured(String providerTransactionId) {
        return new PaymentStatusResult(true, "CAPTURED", providerTransactionId, null);
    }

    public static PaymentStatusResult pending(String providerTransactionId) {
        return new PaymentStatusResult(false, "PENDING_PROVIDER", providerTransactionId, null);
    }

    public static PaymentStatusResult failed(String providerTransactionId, String failureReason) {
        return new PaymentStatusResult(false, "FAILED", providerTransactionId, failureReason);
    }

    public static PaymentStatusResult notFound() {
        return new PaymentStatusResult(false, "NOT_FOUND", null, "Toʻlov provayder tizimida topilmadi");
    }
}
