package uz.hosil.platform.reputation.web;

import jakarta.validation.constraints.NotBlank;

public record ModerateReviewRequest(
        @NotBlank(message = "action is required (HIDE, RESTORE, REMOVE, DISMISS_REPORT)")
        String action,

        @NotBlank(message = "justificationCode is required")
        String justificationCode,

        @NotBlank(message = "adminNotes is required")
        String adminNotes
) {
}
