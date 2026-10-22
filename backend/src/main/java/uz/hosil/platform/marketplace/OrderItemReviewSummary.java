package uz.hosil.platform.marketplace;

import java.util.UUID;

/**
 * Cross-module summary DTO for an order item evaluated in product reviews.
 */
public record OrderItemReviewSummary(
        UUID orderItemId,
        UUID harvestListingId,
        UUID sellerOfferId,
        UUID catalogProductId,
        String itemTitle
) {
}
