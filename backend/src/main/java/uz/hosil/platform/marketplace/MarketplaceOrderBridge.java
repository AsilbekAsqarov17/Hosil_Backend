package uz.hosil.platform.marketplace;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module bridge exposing order validation and summary details to the logistics module.
 */
public interface MarketplaceOrderBridge {

    /**
     * Looks up an order for transport request linking.
     *
     * @param orderId the order ID
     * @return summary of the order if found
     */
    Optional<LinkedOrderSummary> findOrderForTransport(UUID orderId);

    /**
     * Looks up an order for counterparty and product review eligibility.
     *
     * @param orderId the order ID
     * @return review eligibility summary if found
     */
    Optional<OrderReviewEligibilitySummary> findOrderForReview(UUID orderId);

    /**
     * Counts completed orders where the user participated as either buyer or seller.
     *
     * @param userId the user ID
     * @return count of completed orders
     */
    long countCompletedOrdersForUser(UUID userId);
}
