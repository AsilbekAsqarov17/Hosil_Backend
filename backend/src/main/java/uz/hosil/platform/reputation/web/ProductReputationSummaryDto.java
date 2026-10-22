package uz.hosil.platform.reputation.web;

import java.util.Map;
import java.util.UUID;

public record ProductReputationSummaryDto(
        UUID targetListingId,
        double averageRating,
        int totalReviews,
        Map<String, Double> dimensionAverages
) {
}
