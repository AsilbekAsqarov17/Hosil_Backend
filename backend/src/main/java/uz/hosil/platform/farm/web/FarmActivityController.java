package uz.hosil.platform.farm.web;

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
import uz.hosil.platform.farm.service.FarmActivityService;

import java.util.UUID;

/**
 * Controller exposing daily agronomic activities logging and offline batch synchronization.
 */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/activities")
public class FarmActivityController {

    private final FarmActivityService activityService;

    public FarmActivityController(FarmActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    public ResponseEntity<FarmActivityDto> recordActivity(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreateFarmActivityRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FarmActivityService.ActivityCreationResult result = activityService.createActivity(farmId, request, principal.userId());
        if (!result.created()) {
            return ResponseEntity.ok()
                    .header("X-Cache-Lookup", "IDEMPOTENT_HIT")
                    .body(result.dto());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.dto());
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<FarmActivityDto>> listActivities(
            @PathVariable UUID farmId,
            @RequestParam(required = false) UUID cropCycleId,
            @RequestParam(required = false) UUID fieldId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<FarmActivityDto> page = activityService.listActivities(farmId, cropCycleId, fieldId, principal.userId(), pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/batch-sync")
    public ResponseEntity<ActivityBatchSyncResponse> batchSyncActivities(
            @PathVariable UUID farmId,
            @Valid @RequestBody ActivityBatchSyncRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ActivityBatchSyncResponse response = activityService.batchSyncActivities(farmId, request, principal.userId());
        return ResponseEntity.ok(response);
    }
}
