package uz.hosil.platform.identity.web;

import java.util.UUID;

public record OtpChallengeResponse(
        UUID challengeId,
        int expiresInSeconds,
        int resendAfterSeconds
) {}
