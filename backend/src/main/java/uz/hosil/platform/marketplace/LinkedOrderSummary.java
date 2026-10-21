package uz.hosil.platform.marketplace;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public summary of a marketplace order exposed to the logistics module for transport linking.
 * Encapsulates cargo description, unit consistency, and verified backing harvest batch.
 */
public record LinkedOrderSummary(
        UUID orderId,
        String orderNumber,
        UUID buyerUserId,
        UUID sellerUserId,
        UUID harvestBatchId,
        String status,
        String orderFlowType,
        String deliveryType,
        String deliveryAddress,
        String deliveryTerms,
        String cargoDescription,
        BigDecimal totalQuantity,
        String unitCode,
        boolean hasMixedUnits,
        BigDecimal derivedWeightKg
) {}
