package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed DTO containing the full negotiation state, agreed terms, and historical offer sequence.
 */
public record InquiryDetailDto(
        UUID id,
        UUID harvestListingId,
        String cropType,
        String unitCode,
        UUID buyerUserId,
        UUID sellerUserId,
        String status,
        Instant agreedAt,
        String rejectionReason,
        InquiryOfferDto currentOffer,
        InquiryOfferDto agreedOffer,
        List<InquiryOfferDto> offers,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static InquiryDetailDto of(
            InquiryEntity entity,
            InquiryOfferEntity currentOffer,
            InquiryOfferEntity agreedOffer,
            List<InquiryOfferEntity> offers
    ) {
        return new InquiryDetailDto(
                entity.getId(),
                entity.getHarvestListingId(),
                entity.getCropType(),
                entity.getUnitCode(),
                entity.getBuyerUserId(),
                entity.getSellerUserId(),
                entity.getStatus(),
                entity.getAgreedAt(),
                entity.getRejectionReason(),
                InquiryOfferDto.fromEntity(currentOffer),
                InquiryOfferDto.fromEntity(agreedOffer),
                offers.stream().map(InquiryOfferDto::fromEntity).toList(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
