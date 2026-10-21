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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.farm.service.HarvestBatchService;

import java.util.UUID;

@RestController
public class HarvestBatchController {

    private final HarvestBatchService harvestBatchService;

    public HarvestBatchController(HarvestBatchService harvestBatchService) {
        this.harvestBatchService = harvestBatchService;
    }

    @PostMapping("/api/v1/farms/{farmId}/harvest-batches")
    public ResponseEntity<HarvestBatchDto> recordHarvestBatch(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreateHarvestBatchRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        HarvestBatchService.HarvestBatchCreationResult result = harvestBatchService.createHarvestBatch(
                farmId,
                request,
                idempotencyKey,
                principal.userId()
        );

        if (!result.created()) {
            return ResponseEntity.ok()
                    .header("X-Cache-Lookup", "IDEMPOTENT_HIT")
                    .body(result.dto());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result.dto());
    }

    @GetMapping("/api/v1/farms/{farmId}/harvest-batches")
    public ResponseEntity<PageEnvelope<HarvestBatchDto>> listHarvestBatches(
            @PathVariable UUID farmId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<HarvestBatchDto> response = harvestBatchService.listHarvestBatches(farmId, principal.userId(), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/harvest-batches/{batchId}")
    public ResponseEntity<HarvestBatchDto> getHarvestBatch(
            @PathVariable UUID batchId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        HarvestBatchDto dto = harvestBatchService.getHarvestBatch(batchId, principal.userId());
        return ResponseEntity.ok(dto);
    }
}
