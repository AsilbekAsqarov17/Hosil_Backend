package uz.hosil.platform.marketplace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module summary DTO providing order and participant details for review eligibility.
 */
public record OrderReviewEligibilitySummary(
        UUID orderId,
        String orderNumber,
        UUID buyerUserId,
        UUID sellerUserId,
        String orderFlowType,
        String status,
        Instant completedAt,
        String cancellationReason,
        List<OrderItemReviewSummary> items
) {
}
