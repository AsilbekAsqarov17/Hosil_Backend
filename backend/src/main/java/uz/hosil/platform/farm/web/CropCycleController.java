package uz.hosil.platform.farm.web;

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
import uz.hosil.platform.farm.service.CropCycleService;

import java.util.UUID;

/**
 * Controller exposing crop cycle cultivation and lifecycle transition endpoints.
 */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/crop-cycles")
public class CropCycleController {

    private final CropCycleService cropCycleService;

    public CropCycleController(CropCycleService cropCycleService) {
        this.cropCycleService = cropCycleService;
    }

    @PostMapping
    public ResponseEntity<CropCycleDto> createCropCycle(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreateCropCycleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropCycleDto created = cropCycleService.createCropCycle(farmId, request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<CropCycleDto>> listCropCycles(
            @PathVariable UUID farmId,
            @RequestParam(required = false) UUID fieldId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<CropCycleDto> page = cropCycleService.listCropCycles(farmId, fieldId, status, principal.userId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{cycleId}")
    public ResponseEntity<CropCycleDto> getCropCycle(
            @PathVariable UUID farmId,
            @PathVariable UUID cycleId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropCycleDto cycle = cropCycleService.getCropCycle(farmId, cycleId, principal.userId());
        return ResponseEntity.ok(cycle);
    }

    @PatchMapping("/{cycleId}")
    public ResponseEntity<CropCycleDto> updateCropCycle(
            @PathVariable UUID farmId,
            @PathVariable UUID cycleId,
            @Valid @RequestBody UpdateCropCycleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropCycleDto updated = cropCycleService.updateCropCycle(farmId, cycleId, request, principal.userId());
        return ResponseEntity.ok(updated);
    }
}
