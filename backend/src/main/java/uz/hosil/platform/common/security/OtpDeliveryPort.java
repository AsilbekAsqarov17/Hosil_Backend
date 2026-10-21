package uz.hosil.platform.common.security;

/**
 * Replaceable delivery adapter port for verification OTP challenges per DEC-003.
 * Decouples identity verification from specific physical transports (Telegram Bot, SMS gateway, etc.).
 */
public interface OtpDeliveryPort {
    void sendVerificationOtp(String destination, String challengeOtp);
}
