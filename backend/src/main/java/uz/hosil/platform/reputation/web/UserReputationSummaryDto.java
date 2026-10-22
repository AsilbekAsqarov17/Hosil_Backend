package uz.hosil.platform.reputation.web;

import java.util.Map;
import java.util.UUID;

public record UserReputationSummaryDto(
        UUID userId,
        String roleContext,
        double averageRating,
        int totalReviews,
        int totalCompletedOrders,
        Map<String, Double> dimensionAverages
) {
}
