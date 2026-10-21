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
import uz.hosil.platform.farm.service.FarmService;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing farm holding profile and membership management endpoints.
 */
@RestController
@RequestMapping("/api/v1/farms")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @PostMapping
    public ResponseEntity<FarmDto> createFarm(
            @Valid @RequestBody CreateFarmRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FarmDto created = farmService.createFarm(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageEnvelope<FarmDto>> listFarms(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<FarmDto> page = farmService.listAccessibleFarms(principal.userId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{farmId}")
    public ResponseEntity<FarmDto> getFarm(
            @PathVariable UUID farmId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FarmDto farm = farmService.getFarmById(farmId, principal.userId());
        return ResponseEntity.ok(farm);
    }

    @PatchMapping("/{farmId}")
    public ResponseEntity<FarmDto> updateFarm(
            @PathVariable UUID farmId,
            @Valid @RequestBody UpdateFarmRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FarmDto updated = farmService.updateFarm(farmId, request, principal.userId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{farmId}/members")
    public ResponseEntity<List<FarmMemberDto>> listMembers(
            @PathVariable UUID farmId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<FarmMemberDto> members = farmService.listMembers(farmId, principal.userId());
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{farmId}/members")
    public ResponseEntity<FarmMemberDto> addMember(
            @PathVariable UUID farmId,
            @Valid @RequestBody AddFarmMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FarmMemberDto member = farmService.addMember(farmId, request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{farmId}/members/{membershipId}")
    public ResponseEntity<Void> revokeMember(
            @PathVariable UUID farmId,
            @PathVariable UUID membershipId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        farmService.revokeMembership(farmId, membershipId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
