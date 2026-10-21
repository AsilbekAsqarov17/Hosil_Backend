package uz.hosil.platform.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecuritySecretValidatorTests {

    private static final String VALID_PROD_JWT_SECRET = "production_jwt_secret_with_sufficient_entropy_and_at_least_256_bits_length!";
    private static final String VALID_PROD_OTP_SECRET = "production_otp_secret_with_sufficient_entropy_and_at_least_256_bits_length!";

    @Test
    @DisplayName("Non-production profiles allow development default secrets")
    void nonProductionProfilesAllowDevSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local", "test");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                "dev_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256_local_only",
                "dev_otp_hmac_secret_must_be_at_least_256_bits_long_local_only"
        );

        assertDoesNotThrow(validator::validateSecrets);
    }

    @Test
    @DisplayName("Production profile rejects default dev JWT secret")
    void productionRejectsDefaultDevJwtSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                "dev_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256_local_only",
                VALID_PROD_OTP_SECRET
        );

        assertThrows(IllegalStateException.class, validator::validateSecrets);
    }

    @Test
    @DisplayName("Production profile rejects default dev OTP secret")
    void productionRejectsDefaultDevOtpSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                VALID_PROD_JWT_SECRET,
                "dev_otp_hmac_secret_must_be_at_least_256_bits_long_local_only"
        );

        assertThrows(IllegalStateException.class, validator::validateSecrets);
    }

    @Test
    @DisplayName("Production profile rejects weak/short secrets (< 32 characters)")
    void productionRejectsWeakSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                "short_secret_under_256_bits",
                VALID_PROD_OTP_SECRET
        );

        assertThrows(IllegalStateException.class, validator::validateSecrets);
    }

    @Test
    @DisplayName("Production profile rejects identical JWT and OTP secrets")
    void productionRejectsIdenticalSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                VALID_PROD_JWT_SECRET,
                VALID_PROD_JWT_SECRET
        );

        assertThrows(IllegalStateException.class, validator::validateSecrets);
    }

    @Test
    @DisplayName("Production profile accepts valid, distinct secrets with 256-bit entropy")
    void productionAcceptsValidDistinctSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        SecuritySecretValidator validator = new SecuritySecretValidator(
                env,
                VALID_PROD_JWT_SECRET,
                VALID_PROD_OTP_SECRET
        );

        assertDoesNotThrow(validator::validateSecrets);
    }
}
