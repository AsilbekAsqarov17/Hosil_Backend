package uz.hosil.platform.reputation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(
        @NotBlank(message = "reasonCode is required")
        String reasonCode,

        @Size(max = 2000, message = "details must not exceed 2000 characters")
        String details
) {
}
