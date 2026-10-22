package uz.hosil.platform.marketplace.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validates payment subsystem configuration at application startup.
 * Enforces production safety gates:
 * 1. Rejects active sandbox-simulation mode in production profiles.
 * 2. In production, ensures payment provider secrets are configured and do not use default dev values.
 * 3. Enforces minimum 256-bit secret length requirement for HMAC security.
 */
@Component
public class PaymentConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfigValidator.class);
    public static final String DEFAULT_DEV_SANDBOX_SECRET = "sandbox_secret_hosil_2026";
    public static final int MIN_SECRET_LENGTH = 32; // 256 bits

    private final Environment environment;
    private final boolean simulationEnabled;
    private final String sandboxSecret;

    public PaymentConfigValidator(
            Environment environment,
            @Value("${hosil.payment.sandbox.simulation-enabled:true}") boolean simulationEnabled,
            @Value("${hosil.payment.sandbox.secret-key:${hosil.payment.mock.secret-key:sandbox_secret_hosil_2026}}") String sandboxSecret
    ) {
        this.environment = environment;
        this.simulationEnabled = simulationEnabled;
        this.sandboxSecret = sandboxSecret;
    }

    @PostConstruct
    public void validatePaymentConfig() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isProduction = activeProfiles.contains("prod") || activeProfiles.contains("production");

        if (isProduction) {
            // Production Gate 1: Sandbox simulation MUST be disabled in production
            if (simulationEnabled) {
                throw new IllegalStateException(
                        "Production startup failed: hosil.payment.sandbox.simulation-enabled must be false in production profile."
                );
            }

            // Production Gate 2: Sandbox secret must not use default dev placeholder
            if (DEFAULT_DEV_SANDBOX_SECRET.equals(sandboxSecret)) {
                throw new IllegalStateException(
                        "Production startup failed: HOSIL_PAYMENT_SANDBOX_SECRET must not use the insecure default development secret."
                );
            }

            if (sandboxSecret != null && !sandboxSecret.isBlank() && sandboxSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "Production startup failed: payment webhook secret must be at least 256 bits (32 characters)."
                );
            }

            log.info("Production payment configuration successfully validated (simulation disabled, secrets secured).");
        } else {
            if (simulationEnabled) {
                log.info("Sandbox payment simulation is enabled for development/testing.");
            }
        }
    }
}
