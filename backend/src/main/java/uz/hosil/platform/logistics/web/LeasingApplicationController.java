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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.logistics.service.EquipmentLeasingService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics/leasing/applications")
public class LeasingApplicationController {

    private final EquipmentLeasingService leasingService;

    public LeasingApplicationController(EquipmentLeasingService leasingService) {
        this.leasingService = leasingService;
    }

    private boolean checkAdmin(UserPrincipal principal) {
        return principal != null && (principal.hasRole("ADMINISTRATOR") || principal.hasRole("ROLE_ADMINISTRATOR"));
    }

    @PostMapping
    public ResponseEntity<LeasingApplicationDto> submitApplication(
            @Valid @RequestBody SubmitLeasingApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingApplicationDto dto = leasingService.submitApplication(request, principal.userId(), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/draft")
    public ResponseEntity<LeasingApplicationDto> saveDraft(
            @Valid @RequestBody SubmitLeasingApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingApplicationDto dto = leasingService.submitApplication(request, principal.userId(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeasingApplicationDto> getApplication(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingApplicationDto dto = leasingService.getApplication(id, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<LeasingApplicationDto>> listMyApplications(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageEnvelope<LeasingApplicationDto> list = leasingService.listMyApplications(principal.userId(), status, pageable);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<LeasingApplicationDto> withdrawApplication(
            @PathVariable UUID id,
            @RequestBody(required = false) WithdrawApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingApplicationDto dto = leasingService.withdrawApplication(id, principal.userId(), request, checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/simulate-decision")
    public ResponseEntity<LeasingApplicationDto> simulateDecision(
            @PathVariable UUID id,
            @Valid @RequestBody PartnerDecisionSimulationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingApplicationDto dto = leasingService.processPartnerDecision(id, request, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }
}
