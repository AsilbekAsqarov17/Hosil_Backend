package uz.hosil.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for querying Telegram linking status bound to a cryptographic session token.
 * Prevents phone-number enumeration attacks per Milestone 6C.5 security hardening.
 */
public record TelegramLinkStatusRequest(
        @NotBlank(message = "Sessiya tokeni koʻrsatilishi shart")
        String sessionToken
) {}
