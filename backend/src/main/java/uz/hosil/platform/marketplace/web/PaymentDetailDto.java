package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.PaymentEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detailed representation of a payment transaction (PAY-001, PAY-002).
 */
public record PaymentDetailDto(
        UUID id,
        UUID orderId,
        UUID buyerUserId,
        String paymentIntentReference,
        String providerName,
        String providerTransactionId,
        BigDecimal amount,
        String currency,
        String status,
        String idempotencyKey,
        String failureReason,
        Instant expiresAt,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentDetailDto fromEntity(PaymentEntity payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentDetailDto(
                payment.getId(),
                payment.getOrderId(),
                payment.getBuyerUserId(),
                payment.getPaymentIntentReference(),
                payment.getProviderName(),
                payment.getProviderTransactionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getFailureReason(),
                payment.getExpiresAt(),
                payment.getConfirmedAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
