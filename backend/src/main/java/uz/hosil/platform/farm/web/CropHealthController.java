package uz.hosil.platform.farm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.farm.domain.CropDiseaseDiagnosisEntity;
import uz.hosil.platform.farm.domain.FarmActivityEntity;
import uz.hosil.platform.farm.service.CropHealthDiagnosticsService;

import java.util.UUID;

/**
 * Controller exposing AI crop-disease diagnosis endpoints, history retrieval,
 * and agronomic activity conversions.
 * Conforms to docs/01_prd.md AI-001..AI-007 and docs/02_architecture.md §8.4.
 */
@RestController
@RequestMapping("/api/v1/farm/crop-health")
public class CropHealthController {

    private final CropHealthDiagnosticsService diagnosticsService;

    public CropHealthController(CropHealthDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    /**
     * Ingests a crop leaf image for diagnostic scanning.
     */
    @PostMapping(value = "/diagnoses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CropDiseaseDiagnosisDto> submitDiagnosis(
            @RequestPart("image") MultipartFile image,
            @RequestParam("cropType") String cropType,
            @RequestParam(value = "farmId", required = false) UUID farmId,
            @RequestParam(value = "fieldId", required = false) UUID fieldId,
            @RequestParam(value = "cropCycleId", required = false) UUID cropCycleId,
            @RequestParam(value = "userNotes", required = false) String userNotes,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropDiseaseDiagnosisEntity entity = diagnosticsService.submitDiagnosis(
                principal.userId(),
                image,
                cropType,
                farmId,
                fieldId,
                cropCycleId,
                userNotes,
                idempotencyKey
        );

        CropDiseaseDiagnosisDto dto = CropDiseaseDiagnosisDto.fromEntity(entity);
        if ("PROCESSING".equals(entity.getStatus()) || "QUEUED".equals(entity.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Retrieves status and results of a diagnosis.
     */
    @GetMapping("/diagnoses/{id}")
    public ResponseEntity<CropDiseaseDiagnosisDto> getDiagnosis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropDiseaseDiagnosisEntity entity = diagnosticsService.getDiagnosis(id, principal.userId());
        return ResponseEntity.ok(CropDiseaseDiagnosisDto.fromEntity(entity));
    }

    /**
     * Lists diagnosis history for the user or a specific farm.
     */
    @GetMapping("/diagnoses")
    public ResponseEntity<PageEnvelope<CropDiseaseDiagnosisDto>> listDiagnoses(
            @RequestParam(value = "farmId", required = false) UUID farmId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<CropDiseaseDiagnosisEntity> page = diagnosticsService.listUserDiagnoses(
                principal.userId(),
                farmId,
                pageable
        );
        return ResponseEntity.ok(PageEnvelope.from(page.map(CropDiseaseDiagnosisDto::fromEntity)));
    }

    /**
     * Retries diagnosis inference for failed scans without re-uploading the image.
     */
    @PostMapping("/diagnoses/{id}/retry")
    public ResponseEntity<CropDiseaseDiagnosisDto> retryDiagnosis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CropDiseaseDiagnosisEntity entity = diagnosticsService.retryDiagnosis(id, principal.userId());
        return ResponseEntity.ok(CropDiseaseDiagnosisDto.fromEntity(entity));
    }

    /**
     * Converts a completed diagnosis into a farm activity (scouting or pesticide treatment).
     */
    @PostMapping("/diagnoses/{id}/convert-to-activity")
    public ResponseEntity<FarmActivityDto> convertToActivity(
            @PathVariable UUID id,
            @RequestBody(required = false) ConvertToActivityRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String activityType = request != null ? request.activityType() : null;
        String notes = request != null ? request.additionalNotes() : null;

        FarmActivityEntity activity = diagnosticsService.convertToActivity(
                id,
                principal.userId(),
                activityType,
                notes
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(FarmActivityDto.fromEntity(activity));
    }

    /**
     * Serves the sanitized private leaf image binary with strict authorization checks.
     */
    @GetMapping(value = "/diagnoses/{id}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getImage(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        byte[] imageBytes = diagnosticsService.getImageBytes(id, principal.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"diagnosis-" + id + ".jpg\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(imageBytes);
    }
}
