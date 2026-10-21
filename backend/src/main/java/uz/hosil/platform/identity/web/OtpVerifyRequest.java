package uz.hosil.platform.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record OtpVerifyRequest(
        @NotNull(message = "Challenge ID kiritilishi shart")
        UUID challengeId,

        @NotBlank(message = "OTP kodi kiritilishi shart")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP 6 ta raqamdan iborat boʻlishi kerak")
        String otpCode,

        String deviceFingerprint,
        String userAgent
) {}
