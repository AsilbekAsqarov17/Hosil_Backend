package uz.hosil.platform.marketplace.web;

import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed DTO representation of a wholesale order including items, reservation, and audit history.
 */
public record OrderDetailDto(
        UUID id,
        String orderNumber,
        UUID buyerUserId,
        UUID sellerUserId,
        UUID inquiryId,
        UUID agreedOfferId,
        UUID harvestListingId,
        String orderFlowType,
        BigDecimal totalAmount,
        String currency,
        String status,
        String deliveryType,
        String deliveryAddress,
        String deliveryTerms,
        String notes,
        String rejectionReason,
        String cancellationReason,
        Instant confirmationExpiresAt,
        Instant paymentExpiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant completedAt,
        List<OrderItemDto> items,
        InventoryReservationDto reservation,
        List<InventoryReservationDto> reservations,
        List<OrderStatusHistoryDto> history,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderDetailDto fromEntity(
            OrderEntity order,
            List<OrderItemEntity> items,
            InventoryReservationEntity reservation,
            List<OrderStatusHistoryEntity> history
    ) {
        List<InventoryReservationEntity> resList = reservation != null ? List.of(reservation) : List.of();
        return fromEntity(order, items, resList, history);
    }

    public static OrderDetailDto fromEntity(
            OrderEntity order,
            List<OrderItemEntity> items,
            List<InventoryReservationEntity> reservations,
            List<OrderStatusHistoryEntity> history
    ) {
        InventoryReservationDto primaryRes = (reservations != null && !reservations.isEmpty())
                ? InventoryReservationDto.fromEntity(reservations.get(0))
                : null;
        List<InventoryReservationDto> resDtos = reservations != null
                ? reservations.stream().map(InventoryReservationDto::fromEntity).toList()
                : List.of();

        return new OrderDetailDto(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerUserId(),
                order.getSellerUserId(),
                order.getInquiryId(),
                order.getAgreedOfferId(),
                order.getHarvestListingId(),
                order.getOrderFlowType(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getDeliveryType(),
                order.getDeliveryAddress(),
                order.getDeliveryTerms(),
                order.getNotes(),
                order.getRejectionReason(),
                order.getCancellationReason(),
                order.getConfirmationExpiresAt(),
                order.getPaymentExpiresAt(),
                order.getConfirmedAt(),
                order.getCancelledAt(),
                order.getCompletedAt(),
                items != null ? items.stream().map(OrderItemDto::fromEntity).toList() : List.of(),
                primaryRes,
                resDtos,
                history != null ? history.stream().map(OrderStatusHistoryDto::fromEntity).toList() : List.of(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
