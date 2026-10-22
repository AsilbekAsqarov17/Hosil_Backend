package uz.hosil.platform.reputation.web;

import java.util.List;
import java.util.UUID;

public record OrderReviewsSummaryDto(
        UUID orderId,
        boolean hasBuyerSubmitted,
        boolean hasSellerSubmitted,
        boolean isPublished,
        List<ReviewDto> reviews
) {
}
