package uz.hosil.platform.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test SMS provider that captures dispatched OTP codes for deterministic integration tests.
 * Active for default/NOOP testing; backs off when TELEGRAM delivery provider is explicitly configured.
 */
@Component
@Primary
@ConditionalOnExpression("!'${hosil.otp.delivery.provider:${hosil.sms.provider:NOOP}}'.equalsIgnoreCase('TELEGRAM')")
public class TestSmsCaptureProvider implements SmsProviderPort {

    private final ConcurrentHashMap<String, String> capturedOtps = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationOtp(String phoneNumber, String challengeOtp) {
        capturedOtps.put(phoneNumber, challengeOtp);
    }

    public String getLastOtp(String phoneNumber) {
        return capturedOtps.get(phoneNumber);
    }

    public void clear() {
        capturedOtps.clear();
    }
}
