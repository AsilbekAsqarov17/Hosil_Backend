package uz.hosil.platform.identity.web;

/**
 * Minimal status response for Telegram linking check.
 * Strictly omits phone numbers, chat IDs, and Telegram user IDs to prevent disclosure.
 */
public record TelegramLinkStatusResponse(
        boolean isLinked
) {}
