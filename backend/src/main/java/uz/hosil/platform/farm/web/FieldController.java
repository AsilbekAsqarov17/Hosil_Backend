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
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.farm.service.FieldService;

import java.util.UUID;

/**
 * Controller exposing field plot management endpoints.
 */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/fields")
public class FieldController {

    private final FieldService fieldService;

    public FieldController(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @PostMapping
    public ResponseEntity<FieldDto> createField(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreateFieldRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FieldDto created = fieldService.createField(farmId, request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<FieldDto>> listFields(
            @PathVariable UUID farmId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<FieldDto> page = fieldService.listFields(farmId, principal.userId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{fieldId}")
    public ResponseEntity<FieldDto> getField(
            @PathVariable UUID farmId,
            @PathVariable UUID fieldId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FieldDto field = fieldService.getField(farmId, fieldId, principal.userId());
        return ResponseEntity.ok(field);
    }

    @PatchMapping("/{fieldId}")
    public ResponseEntity<FieldDto> updateField(
            @PathVariable UUID farmId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody UpdateFieldRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FieldDto updated = fieldService.updateField(farmId, fieldId, request, principal.userId());
        return ResponseEntity.ok(updated);
    }
}
