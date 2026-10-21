package uz.hosil.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "Yangilash kaliti kiritilishi shart")
        String refreshToken,

        String deviceFingerprint
) {}
