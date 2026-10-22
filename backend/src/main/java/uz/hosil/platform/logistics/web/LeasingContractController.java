package uz.hosil.platform.logistics.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.logistics.service.EquipmentLeasingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics/leasing/contracts")
public class LeasingContractController {

    private final EquipmentLeasingService leasingService;

    public LeasingContractController(EquipmentLeasingService leasingService) {
        this.leasingService = leasingService;
    }

    private boolean checkAdmin(UserPrincipal principal) {
        return principal != null && (principal.hasRole("ADMINISTRATOR") || principal.hasRole("ROLE_ADMINISTRATOR"));
    }

    @PostMapping("/prepare/{applicationId}")
    public ResponseEntity<LeasingContractDto> prepareContract(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingContractDto dto = leasingService.prepareContract(applicationId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/{contractId}/sign")
    public ResponseEntity<LeasingContractDto> signContract(
            @PathVariable UUID contractId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingContractDto dto = leasingService.signContract(contractId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<LeasingContractDto> getContract(
            @PathVariable UUID contractId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingContractDto dto = leasingService.getContract(contractId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<LeasingContractDto> getContractByApplication(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LeasingContractDto dto = leasingService.getContractByApplicationId(applicationId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{contractId}/repayment-schedule")
    public ResponseEntity<List<RepaymentScheduleItemDto>> getRepaymentSchedule(
            @PathVariable UUID contractId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<RepaymentScheduleItemDto> list = leasingService.getRepaymentSchedule(contractId, principal.userId(), checkAdmin(principal));
        return ResponseEntity.ok(list);
    }
}
