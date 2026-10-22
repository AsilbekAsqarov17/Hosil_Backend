package uz.hosil.platform.logistics.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.logistics.service.EquipmentLeasingService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics/equipment")
public class EquipmentListingController {

    private final EquipmentLeasingService leasingService;

    public EquipmentListingController(EquipmentLeasingService leasingService) {
        this.leasingService = leasingService;
    }

    private boolean checkAdmin(UserPrincipal principal) {
        return principal != null && (principal.hasRole("ADMINISTRATOR") || principal.hasRole("ROLE_ADMINISTRATOR"));
    }

    @PostMapping
    public ResponseEntity<EquipmentListingDto> createEquipment(
            @Valid @RequestBody CreateEquipmentListingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EquipmentListingDto created = leasingService.createEquipmentListing(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentListingDto> getEquipment(@PathVariable UUID id) {
        return ResponseEntity.ok(leasingService.getEquipmentListing(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentListingDto> updateEquipment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEquipmentListingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EquipmentListingDto updated = leasingService.updateEquipmentListing(id, request, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<EquipmentListingDto>> listEquipment(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String availabilityStatus,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(leasingService.listEquipmentListings(category, region, availabilityStatus, pageable));
    }
}
