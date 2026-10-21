package uz.hosil.platform.common.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validates security secrets at application startup.
 * Enforces that in production profiles:
 * 1. HOSIL_JWT_SECRET and HOSIL_OTP_SECRET are provided and not using insecure defaults.
 * 2. Secrets meet the minimum 256-bit (32 characters / bytes) length requirement for HMAC-SHA256.
 * 3. HOSIL_JWT_SECRET and HOSIL_OTP_SECRET must be strictly decoupled and distinct.
 */
@Component
public class SecuritySecretValidator {

    private static final Logger log = LoggerFactory.getLogger(SecuritySecretValidator.class);

    private static final String DEFAULT_DEV_JWT_SECRET = "dev_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256_local_only";
    private static final String DEFAULT_DEV_OTP_SECRET = "dev_otp_hmac_secret_must_be_at_least_256_bits_long_local_only";
    private static final int MIN_SECRET_LENGTH = 32; // 256 bits

    private final Environment environment;
    private final String jwtSecret;
    private final String otpSecret;

    public SecuritySecretValidator(
            Environment environment,
            @Value("${hosil.security.jwt.secret:}") String jwtSecret,
            @Value("${hosil.security.otp.secret:}") String otpSecret
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.otpSecret = otpSecret;
    }

    @PostConstruct
    public void validateSecrets() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isProduction = activeProfiles.contains("prod") || activeProfiles.contains("production");

        if (isProduction) {
            if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.equals(DEFAULT_DEV_JWT_SECRET)) {
                throw new IllegalStateException("Production startup failed: HOSIL_JWT_SECRET must be set to a secure, non-default value.");
            }
            if (jwtSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("Production startup failed: HOSIL_JWT_SECRET must be at least 256 bits (32 characters).");
            }
            if (otpSecret == null || otpSecret.isBlank() || otpSecret.equals(DEFAULT_DEV_OTP_SECRET)) {
                throw new IllegalStateException("Production startup failed: HOSIL_OTP_SECRET must be set to a secure, non-default value.");
            }
            if (otpSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("Production startup failed: HOSIL_OTP_SECRET must be at least 256 bits (32 characters).");
            }
            if (jwtSecret.equals(otpSecret)) {
                throw new IllegalStateException("Production startup failed: HOSIL_JWT_SECRET and HOSIL_OTP_SECRET must be strictly distinct and decoupled.");
            }
            log.info("Production security secrets successfully validated and verified distinct.");
        } else {
            if (jwtSecret != null && jwtSecret.equals(otpSecret) && !DEFAULT_DEV_JWT_SECRET.equals(jwtSecret)) {
                log.warn("Security Warning: HOSIL_JWT_SECRET and HOSIL_OTP_SECRET are identical. Separate secrets must be configured for staging and production.");
            }
        }
    }
}
