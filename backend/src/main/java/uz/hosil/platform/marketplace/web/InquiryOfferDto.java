package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an immutable offer/counteroffer in the negotiation history.
 */
public record InquiryOfferDto(
        UUID id,
        Integer offerNumber,
        UUID proposerUserId,
        String proposerRole,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalPrice,
        String deliveryTerms,
        String notes,
        Instant expiresAt,
        String status,
        Instant createdAt
) {
    public static InquiryOfferDto fromEntity(InquiryOfferEntity entity) {
        if (entity == null) return null;
        return new InquiryOfferDto(
                entity.getId(),
                entity.getOfferNumber(),
                entity.getProposerUserId(),
                entity.getProposerRole(),
                entity.getQuantity(),
                entity.getUnitCode(),
                entity.getUnitPrice(),
                entity.getCurrency(),
                entity.getTotalPrice(),
                entity.getDeliveryTerms(),
                entity.getNotes(),
                entity.getExpiresAt(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
