package uz.hosil.platform.reputation.web;

import jakarta.validation.Valid;
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
import uz.hosil.platform.reputation.domain.ReviewType;
import uz.hosil.platform.reputation.service.ReputationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for transaction-verified reviews, rebuttals, reporting, and public reputation queries.
 * Conforms to docs/12_reputation_and_reviews.md §6.
 */
@RestController
@RequestMapping("/api/v1")
public class ReputationController {

    private final ReputationService reputationService;

    public ReputationController(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @PostMapping("/orders/{orderId}/reviews")
    public ResponseEntity<ReviewDto> submitReview(
            @PathVariable UUID orderId,
            @Valid @RequestBody SubmitReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReviewDto dto = reputationService.submitReview(orderId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/orders/{orderId}/reviews")
    public ResponseEntity<OrderReviewsSummaryDto> getOrderReviews(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrderReviewsSummaryDto summary = reputationService.getOrderReviewsSummary(orderId, principal.userId());
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/reputation/reviews/{reviewId}/rebuttal")
    public ResponseEntity<ReviewResponseDto> submitRebuttal(
            @PathVariable UUID reviewId,
            @Valid @RequestBody SubmitRebuttalRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReviewResponseDto dto = reputationService.submitRebuttal(reviewId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/reputation/reviews/{reviewId}/reports")
    public ResponseEntity<Map<String, Object>> reportReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReportReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reportId = reputationService.reportReview(reviewId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "reportId", reportId,
                "message", "Shikoyat qabul qilindi va moderator koʻrib chiqishiga yuborildi"
        ));
    }

    @GetMapping("/reputation/users/{userId}")
    public ResponseEntity<UserReputationSummaryDto> getUserReputation(
            @PathVariable UUID userId,
            @RequestParam(value = "role", defaultValue = "SELLER") String role
    ) {
        return ResponseEntity.ok(reputationService.getUserReputation(userId, role));
    }

    @GetMapping("/reputation/users/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getUserReviews(
            @PathVariable UUID userId,
            @RequestParam(value = "type", required = false) ReviewType reviewType
    ) {
        return ResponseEntity.ok(reputationService.getUserReviews(userId, reviewType));
    }

    @GetMapping("/reputation/listings/{listingId}")
    public ResponseEntity<ProductReputationSummaryDto> getListingReputation(
            @PathVariable UUID listingId
    ) {
        return ResponseEntity.ok(reputationService.getListingReputation(listingId));
    }

    @GetMapping("/reputation/listings/{listingId}/reviews")
    public ResponseEntity<List<ReviewDto>> getListingReviews(
            @PathVariable UUID listingId
    ) {
        return ResponseEntity.ok(reputationService.getListingReviews(listingId));
    }
}
