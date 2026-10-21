package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for paginated inquiry lists (buyer or seller dashboard).
 */
public record InquirySummaryDto(
        UUID id,
        UUID harvestListingId,
        String cropType,
        String unitCode,
        UUID buyerUserId,
        UUID sellerUserId,
        String status,
        Integer currentOfferNumber,
        String currentOfferProposerRole,
        BigDecimal currentQuantity,
        BigDecimal currentUnitPrice,
        String currentCurrency,
        Instant agreedAt,
        Instant lastOfferAt,
        Instant createdAt
) {
    public static InquirySummaryDto fromEntity(InquiryEntity inquiry, InquiryOfferEntity currentOffer) {
        return new InquirySummaryDto(
                inquiry.getId(),
                inquiry.getHarvestListingId(),
                inquiry.getCropType(),
                inquiry.getUnitCode(),
                inquiry.getBuyerUserId(),
                inquiry.getSellerUserId(),
                inquiry.getStatus(),
                currentOffer != null ? currentOffer.getOfferNumber() : null,
                currentOffer != null ? currentOffer.getProposerRole() : null,
                currentOffer != null ? currentOffer.getQuantity() : null,
                currentOffer != null ? currentOffer.getUnitPrice() : null,
                currentOffer != null ? currentOffer.getCurrency() : null,
                inquiry.getAgreedAt(),
                currentOffer != null ? currentOffer.getCreatedAt() : inquiry.getCreatedAt(),
                inquiry.getCreatedAt()
        );
    }
}
