package uz.hosil.platform.logistics.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.logistics.service.LogisticsShipmentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics/carrier-profile")
public class CarrierProfileController {

    private final LogisticsShipmentService shipmentService;

    public CarrierProfileController(LogisticsShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    private boolean checkAdmin(UserPrincipal principal) {
        return principal != null && (principal.hasRole("ADMINISTRATOR") || principal.hasRole("ROLE_ADMINISTRATOR") || principal.hasRole("ADMIN"));
    }

    @GetMapping
    public ResponseEntity<CarrierProfileDto> getMyCarrierProfile(@AuthenticationPrincipal UserPrincipal principal) {
        CarrierProfileDto profile = shipmentService.getCarrierProfile(principal.userId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<CarrierProfileDto> upsertMyCarrierProfile(
            @Valid @RequestBody CreateCarrierProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CarrierProfileDto updated = shipmentService.upsertCarrierProfile(principal.userId(), request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{carrierUserId}/verification")
    public ResponseEntity<CarrierProfileDto> updateCarrierVerification(
            @PathVariable UUID carrierUserId,
            @Valid @RequestBody UpdateCarrierVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (!checkAdmin(principal)) {
            throw new AccessDeniedException("Only administrators can update carrier verification status");
        }
        CarrierProfileDto updated = shipmentService.adminUpdateCarrierVerification(carrierUserId, request.status());
        return ResponseEntity.ok(updated);
    }
}
