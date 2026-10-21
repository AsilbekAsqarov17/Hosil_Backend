package uz.hosil.platform.identity.web;

public record TelegramLinkSessionResponse(
        String token,
        String deepLink,
        String botUsername,
        int expiresInSeconds
) {}
