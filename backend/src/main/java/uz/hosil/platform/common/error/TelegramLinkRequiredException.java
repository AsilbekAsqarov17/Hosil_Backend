package uz.hosil.platform.common.error;

/**
 * Thrown when an OTP request is attempted for a phone number that has not yet
 * been linked to a verified Telegram chat per Milestone 6C.5 and DEC-003.
 * Mapped to HTTP 428 Precondition Required with code TELEGRAM_LINK_REQUIRED.
 */
public class TelegramLinkRequiredException extends RuntimeException {
    public TelegramLinkRequiredException(String message) {
        super(message);
    }
}
