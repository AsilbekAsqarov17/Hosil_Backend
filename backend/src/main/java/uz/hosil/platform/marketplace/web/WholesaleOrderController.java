package uz.hosil.platform.marketplace.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.marketplace.service.WholesaleOrderService;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing wholesale order placement, confirmation, rejection,
 * cancellation, and tracking endpoints on Hosil Bozor (DEC-006, DEC-010).
 */
@RestController
@RequestMapping("/api/v1/market/orders")
public class WholesaleOrderController {

    private final WholesaleOrderService orderService;

    public WholesaleOrderController(WholesaleOrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Buyer creates a wholesale purchase order from an agreed RFQ inquiry.
     */
    @PostMapping("/from-rfq")
    public ResponseEntity<OrderDetailDto> createOrderFromRfq(
            @Valid @RequestBody CreateWholesaleOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        WholesaleOrderService.OrderCreationResult result = orderService.createOrderFromRfq(
                request,
                idempotencyKey,
                principal.userId()
        );

        if (result.isIdempotentHit()) {
            return ResponseEntity.ok()
                    .header("X-Cache-Lookup", "IDEMPOTENT_HIT")
                    .body(result.orderDetail());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result.orderDetail());
    }

    /**
     * Lists paginated wholesale orders for the authenticated caller.
     */
    @GetMapping
    public ResponseEntity<PageEnvelope<OrderSummaryDto>> listMyOrders(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageEnvelope<OrderSummaryDto> page = orderService.listMyOrders(
                role,
                status,
                pageable,
                principal.userId()
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieves complete wholesale order detail.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailDto> getOrderDetail(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrderDetailDto detail = orderService.getOrderDetail(orderId, principal.userId());
        return ResponseEntity.ok(detail);
    }

    /**
     * Seller operationally confirms order, transitioning to CONFIRMED.
     */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderDetailDto> confirmOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrderDetailDto detail = orderService.confirmOrder(orderId, principal.userId());
        return ResponseEntity.ok(detail);
    }

    /**
     * Seller rejects order, releasing reserved stock.
     */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<OrderDetailDto> rejectOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.reason() : null;
        OrderDetailDto detail = orderService.rejectOrder(orderId, reason, principal.userId());
        return ResponseEntity.ok(detail);
    }

    /**
     * Buyer or seller cancels order per stage-gated rules (DEC-010).
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailDto> cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.reason() : null;
        OrderDetailDto detail = orderService.cancelOrder(orderId, reason, principal.userId());
        return ResponseEntity.ok(detail);
    }

    /**
     * Retrieves chronological order audit history.
     */
    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<OrderStatusHistoryDto>> getOrderHistory(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<OrderStatusHistoryDto> history = orderService.getOrderHistory(orderId, principal.userId());
        return ResponseEntity.ok(history);
    }
}
