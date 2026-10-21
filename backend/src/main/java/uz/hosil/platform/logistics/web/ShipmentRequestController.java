package uz.hosil.platform.logistics.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.logistics.service.LogisticsShipmentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics/shipment-requests")
public class ShipmentRequestController {

    private final LogisticsShipmentService shipmentService;

    public ShipmentRequestController(LogisticsShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    private boolean checkAdmin(UserPrincipal principal) {
        return principal != null && (principal.hasRole("ADMINISTRATOR") || principal.hasRole("ROLE_ADMINISTRATOR"));
    }

    @PostMapping
    public ResponseEntity<ShipmentRequestDetailDto> createShipmentRequest(
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShipmentRequestDetailDto created = shipmentService.createShipmentRequest(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ShipmentRequestDetailDto> getShipmentRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShipmentRequestDetailDto dto = shipmentService.getShipmentRequest(requestId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<ShipmentRequestSummaryDto>> listShipmentRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String originRegion,
            @RequestParam(required = false) String destinationRegion,
            @RequestParam(required = false) Boolean mineOnly,
            @RequestParam(required = false) Boolean assignedToMe,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<ShipmentRequestSummaryDto> page = shipmentService.listShipmentRequests(
                status,
                originRegion,
                destinationRegion,
                mineOnly,
                assignedToMe,
                principal.userId(),
                pageable
        );
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<ShipmentRequestDetailDto> acceptShipmentRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShipmentRequestDetailDto accepted = shipmentService.acceptShipmentRequest(requestId, principal.userId());
        return ResponseEntity.ok(accepted);
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<ShipmentRequestDetailDto> updateShipmentStatus(
            @PathVariable UUID requestId,
            @Valid @RequestBody UpdateShipmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShipmentRequestDetailDto updated = shipmentService.updateShipmentStatus(
                requestId,
                request,
                principal.userId(),
                checkAdmin(principal)
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ShipmentRequestDetailDto> cancelShipmentRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody CancelShipmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShipmentRequestDetailDto cancelled = shipmentService.cancelShipmentRequest(
                requestId,
                request,
                principal.userId(),
                checkAdmin(principal)
        );
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/{requestId}/history")
    public ResponseEntity<List<ShipmentStatusHistoryDto>> getShipmentHistory(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ShipmentStatusHistoryDto> history = shipmentService.getShipmentHistory(
                requestId,
                principal.userId(),
                checkAdmin(principal)
        );
        return ResponseEntity.ok(history);
    }
}
