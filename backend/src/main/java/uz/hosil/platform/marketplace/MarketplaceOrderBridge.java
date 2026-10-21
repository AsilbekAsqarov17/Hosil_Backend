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
}
