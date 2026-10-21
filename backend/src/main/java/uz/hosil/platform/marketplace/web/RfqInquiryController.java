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
import uz.hosil.platform.marketplace.service.RfqNegotiationService;

import java.util.UUID;

/**
 * Controller exposing buyer inquiries and RFQ negotiation endpoints on Hosil Bozor.
 */
@RestController
public class RfqInquiryController {

    private final RfqNegotiationService negotiationService;

    public RfqInquiryController(RfqNegotiationService negotiationService) {
        this.negotiationService = negotiationService;
    }

    /**
     * Buyer initiates an inquiry and initial offer for an RFQ-enabled harvest listing.
     */
    @PostMapping("/api/v1/market/harvest-listings/{listingId}/inquiries")
    public ResponseEntity<InquiryDetailDto> submitInquiry(
            @PathVariable UUID listingId,
            @Valid @RequestBody CreateInquiryRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RfqNegotiationService.InquiryOperationResult result = negotiationService.submitInquiry(
                listingId,
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

    /**
     * Lists paginated inquiries where the authenticated user is buyer or seller.
     */
    @GetMapping("/api/v1/market/inquiries")
    public ResponseEntity<PageEnvelope<InquirySummaryDto>> listMyInquiries(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID listingId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageEnvelope<InquirySummaryDto> page = negotiationService.listMyInquiries(
                role,
                status,
                listingId,
                pageable,
                principal.userId()
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieves full inquiry details with chronological offer history.
     */
    @GetMapping("/api/v1/market/inquiries/{inquiryId}")
    public ResponseEntity<InquiryDetailDto> getInquiryDetail(
            @PathVariable UUID inquiryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        InquiryDetailDto dto = negotiationService.getInquiryDetail(inquiryId, principal.userId());
        return ResponseEntity.ok(dto);
    }

    /**
     * Submits a counteroffer in turn-taking sequence.
     */
    @PostMapping("/api/v1/market/inquiries/{inquiryId}/offers")
    public ResponseEntity<InquiryDetailDto> submitCounterOffer(
            @PathVariable UUID inquiryId,
            @Valid @RequestBody SubmitOfferRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RfqNegotiationService.InquiryOperationResult result = negotiationService.submitCounterOffer(
                inquiryId,
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

    /**
     * Recipient accepts active proposal, binding commercial terms (DEC-006).
     */
    @PostMapping("/api/v1/market/inquiries/{inquiryId}/accept")
    public ResponseEntity<InquiryDetailDto> acceptOffer(
            @PathVariable UUID inquiryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        InquiryDetailDto dto = negotiationService.acceptOffer(inquiryId, principal.userId());
        return ResponseEntity.ok(dto);
    }

    /**
     * Participant rejects the open inquiry.
     */
    @PostMapping("/api/v1/market/inquiries/{inquiryId}/reject")
    public ResponseEntity<InquiryDetailDto> rejectInquiry(
            @PathVariable UUID inquiryId,
            @RequestBody(required = false) InquiryDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.reason() : null;
        InquiryDetailDto dto = negotiationService.rejectInquiry(inquiryId, reason, principal.userId());
        return ResponseEntity.ok(dto);
    }

    /**
     * Buyer withdraws their open inquiry.
     */
    @PostMapping("/api/v1/market/inquiries/{inquiryId}/withdraw")
    public ResponseEntity<InquiryDetailDto> withdrawInquiry(
            @PathVariable UUID inquiryId,
            @RequestBody(required = false) InquiryDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = request != null ? request.reason() : null;
        InquiryDetailDto dto = negotiationService.withdrawInquiry(inquiryId, reason, principal.userId());
        return ResponseEntity.ok(dto);
    }
}
