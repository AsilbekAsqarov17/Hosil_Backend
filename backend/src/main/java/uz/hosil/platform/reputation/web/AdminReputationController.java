package uz.hosil.platform.reputation.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.reputation.domain.ReviewReportEntity;
import uz.hosil.platform.reputation.domain.ReviewReportStatus;
import uz.hosil.platform.reputation.repository.ReviewReportRepository;
import uz.hosil.platform.reputation.service.ReputationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative moderation controller for review moderation actions and abuse reports.
 * Conforms to docs/12_reputation_and_reviews.md §5.3 (ADM-005).
 */
@RestController
@RequestMapping("/api/v1/admin/reputation")
public class AdminReputationController {

    private final ReputationService reputationService;
    private final ReviewReportRepository reportRepository;

    public AdminReputationController(ReputationService reputationService, ReviewReportRepository reportRepository) {
        this.reputationService = reputationService;
        this.reportRepository = reportRepository;
    }

    private void checkAdminOrSupport(UserPrincipal principal) {
        if (principal == null || (!principal.hasRole("ADMIN") && !principal.hasRole("ADMINISTRATOR")
                && !principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_ADMINISTRATOR")
                && !principal.hasRole("SUPPORT_OPERATOR") && !principal.hasRole("ROLE_SUPPORT_OPERATOR"))) {
            throw new AccessDeniedException("Faqat platforma maʼmurlari yoki qoʻllab-quvvatlash xodimlari uchun ruxsat etilgan");
        }
    }

    @PostMapping("/reviews/{reviewId}/moderate")
    public ResponseEntity<Map<String, String>> moderateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ModerateReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        checkAdminOrSupport(principal);
        reputationService.moderateReview(reviewId, principal.userId(), request);
        return ResponseEntity.ok(Map.of(
                "reviewId", reviewId.toString(),
                "status", "MODERATED",
                "action", request.action()
        ));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReviewReportEntity>> getPendingReports(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        checkAdminOrSupport(principal);
        return ResponseEntity.ok(reportRepository.findByStatusOrderByCreatedAtDesc(ReviewReportStatus.PENDING));
    }
}
