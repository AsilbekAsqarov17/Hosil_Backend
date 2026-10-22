package uz.hosil.platform.reputation.web;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponseDto(
        UUID id,
        UUID reviewId,
        UUID authorId,
        String responseText,
        Instant createdAt
) {
}
