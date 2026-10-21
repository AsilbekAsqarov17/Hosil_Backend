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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.marketplace.service.SellerOfferService;

import java.util.UUID;

/**
 * Controller exposing commercial input seller offer management endpoints (DEC-004, DEC-005, MKT-003).
 */
@RestController
@RequestMapping("/api/v1/market/seller/offers")
public class SellerOfferController {

    private final SellerOfferService sellerOfferService;

    public SellerOfferController(SellerOfferService sellerOfferService) {
        this.sellerOfferService = sellerOfferService;
    }

    /**
     * Verified supplier creates an offer on a catalog product.
     */
    @PostMapping
    public ResponseEntity<SellerOfferDetailDto> createOffer(
            @Valid @RequestBody CreateSellerOfferRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SellerOfferDetailDto dto = sellerOfferService.createOffer(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Supplier lists their own offers with optional status and product filters.
     */
    @GetMapping
    public ResponseEntity<PageEnvelope<SellerOfferDetailDto>> getMyOffers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID catalogProductId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageEnvelope<SellerOfferDetailDto> page = sellerOfferService.getMyOffers(
                status, catalogProductId, pageable, principal.userId()
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Supplier retrieves details of their own offer.
     */
    @GetMapping("/{offerId}")
    public ResponseEntity<SellerOfferDetailDto> getMyOfferDetail(
            @PathVariable UUID offerId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SellerOfferDetailDto dto = sellerOfferService.getMyOfferDetail(offerId, principal.userId());
        return ResponseEntity.ok(dto);
    }

    /**
     * Supplier updates permitted commercial fields of their offer.
     */
    @PutMapping("/{offerId}")
    public ResponseEntity<SellerOfferDetailDto> updateOffer(
            @PathVariable UUID offerId,
            @RequestBody UpdateSellerOfferRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SellerOfferDetailDto dto = sellerOfferService.updateOffer(offerId, request, principal.userId());
        return ResponseEntity.ok(dto);
    }

    /**
     * Supplier deactivates/archives their offer.
     */
    @PostMapping("/{offerId}/deactivate")
    public ResponseEntity<SellerOfferDetailDto> deactivateOffer(
            @PathVariable UUID offerId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SellerOfferDetailDto dto = sellerOfferService.deactivateOffer(offerId, principal.userId());
        return ResponseEntity.ok(dto);
    }
}
