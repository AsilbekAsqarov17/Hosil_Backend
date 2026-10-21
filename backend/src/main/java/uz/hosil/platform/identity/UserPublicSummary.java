package uz.hosil.platform.identity;

import java.util.UUID;

/**
 * Public DTO representing basic user information across module boundaries.
 */
public record UserPublicSummary(
        UUID userId,
        String phoneNumber,
        String fullName
) {}
