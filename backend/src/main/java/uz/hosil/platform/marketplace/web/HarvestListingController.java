package uz.hosil.platform.marketplace.web;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.marketplace.service.HarvestDiscoveryService;
import uz.hosil.platform.marketplace.service.HarvestListingFilter;
import uz.hosil.platform.marketplace.service.HarvestPublicationService;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Controller exposing harvest listing publication and public search & discovery on Hosil Bozor.
 */
@RestController
@RequestMapping("/api/v1/market/harvest-listings")
public class HarvestListingController {

    private final HarvestPublicationService publicationService;
    private final HarvestDiscoveryService discoveryService;

    public HarvestListingController(
            HarvestPublicationService publicationService,
            HarvestDiscoveryService discoveryService
    ) {
        this.publicationService = publicationService;
        this.discoveryService = discoveryService;
    }

    @PostMapping
    public ResponseEntity<HarvestListingDto> publishHarvestListing(
            @Valid @RequestBody CreateHarvestListingRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        HarvestPublicationService.PublicationResult result = publicationService.publishHarvestListing(
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

    @GetMapping
    public ResponseEntity<PageEnvelope<HarvestListingDto>> searchHarvestListings(
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String qualityGrade,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean isRfqEnabled,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        HarvestListingFilter filter = new HarvestListingFilter(
                cropType,
                region,
                district,
                qualityGrade,
                minPrice,
                maxPrice,
                isRfqEnabled
        );
        PageEnvelope<HarvestListingDto> page = discoveryService.searchPublishedListings(filter, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<HarvestListingDto> getHarvestListingById(@PathVariable UUID listingId) {
        HarvestListingDto dto = discoveryService.getPublishedListingDetail(listingId);
        return ResponseEntity.ok(dto);
    }
}
