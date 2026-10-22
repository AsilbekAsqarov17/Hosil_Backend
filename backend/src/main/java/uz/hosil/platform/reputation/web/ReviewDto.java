package uz.hosil.platform.reputation.web;

import uz.hosil.platform.reputation.domain.ReviewStatus;
import uz.hosil.platform.reputation.domain.ReviewType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID orderId,
        UUID orderItemId,
        UUID authorId,
        UUID targetUserId,
        UUID targetListingId,
        ReviewType reviewType,
        int overallRating,
        Map<String, Integer> dimensionScores,
        String commentary,
        ReviewStatus status,
        boolean counterpartyBlind,
        Instant publishedAt,
        Instant createdAt,
        ReviewResponseDto rebuttal
) {
}
