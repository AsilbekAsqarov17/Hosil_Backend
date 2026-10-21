package uz.hosil.platform.marketplace.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.marketplace.LinkedOrderSummary;
import uz.hosil.platform.marketplace.MarketplaceOrderBridge;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefaultMarketplaceOrderBridge implements MarketplaceOrderBridge {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final HarvestListingRepository harvestListingRepository;

    public DefaultMarketplaceOrderBridge(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            HarvestListingRepository harvestListingRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.harvestListingRepository = harvestListingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LinkedOrderSummary> findOrderForTransport(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }

        Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        OrderEntity order = orderOpt.get();
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);

        String cargoDescription;
        BigDecimal totalQuantity = null;
        String unitCode = null;
        boolean hasMixedUnits = false;
        BigDecimal derivedWeightKg = null;

        if (!items.isEmpty()) {
            cargoDescription = items.stream()
                    .map(OrderItemEntity::getItemSnapshotTitle)
                    .collect(Collectors.joining(", "));

            Set<String> uniqueUnits = items.stream()
                    .map(i -> i.getUnitCode() != null ? i.getUnitCode().trim().toUpperCase(Locale.ROOT) : "UNKNOWN")
                    .collect(Collectors.toSet());

            if (uniqueUnits.size() == 1) {
                unitCode = uniqueUnits.iterator().next();
                hasMixedUnits = false;
                totalQuantity = items.stream()
                        .map(OrderItemEntity::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Weight can strictly and exclusively be derived when unit is canonical mass in KG.
                // Non-mass units (LITER, PIECE, BAG_50KG) or unspecified units must not be treated as KG.
                if ("KG".equalsIgnoreCase(unitCode)) {
                    derivedWeightKg = totalQuantity;
                }
            } else {
                hasMixedUnits = true;
                unitCode = "MIXED";
                totalQuantity = null; // Incompatible units cannot be meaningfully summed
                derivedWeightKg = null;
            }
        } else {
            cargoDescription = "Order #" + order.getOrderNumber();
        }

        // Resolve backing harvest batch if order originated from a wholesale harvest listing
        UUID backingHarvestBatchId = null;
        if (order.getHarvestListingId() != null) {
            backingHarvestBatchId = harvestListingRepository.findById(order.getHarvestListingId())
                    .map(HarvestListingEntity::getHarvestBatchId)
                    .orElse(null);
        } else if (!items.isEmpty()) {
            backingHarvestBatchId = items.stream()
                    .map(OrderItemEntity::getHarvestListingId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .flatMap(harvestListingRepository::findById)
                    .map(HarvestListingEntity::getHarvestBatchId)
                    .orElse(null);
        }

        return Optional.of(new LinkedOrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerUserId(),
                order.getSellerUserId(),
                backingHarvestBatchId,
                order.getStatus(),
                order.getOrderFlowType(),
                order.getDeliveryType(),
                order.getDeliveryAddress(),
                order.getDeliveryTerms(),
                cargoDescription,
                totalQuantity,
                unitCode,
                hasMixedUnits,
                derivedWeightKg
        ));
    }
}
