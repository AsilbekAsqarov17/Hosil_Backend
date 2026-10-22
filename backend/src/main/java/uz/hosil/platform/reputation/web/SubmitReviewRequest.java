package uz.hosil.platform.reputation.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.hosil.platform.reputation.domain.ReviewType;

import java.util.Map;
import java.util.UUID;

public record SubmitReviewRequest(
        @NotNull(message = "reviewType is required")
        ReviewType reviewType,

        UUID orderItemId,

        @Min(value = 1, message = "overallRating must be at least 1")
        @Max(value = 5, message = "overallRating must be at most 5")
        int overallRating,

        Map<String, Integer> dimensionScores,

        @Size(max = 1000, message = "commentary must not exceed 1000 characters")
        String commentary
) {
}
