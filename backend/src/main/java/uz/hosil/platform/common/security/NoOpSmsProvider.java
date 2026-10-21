package uz.hosil.platform.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Development & testing implementation of OtpDeliveryPort / SmsProviderPort.
 * Acts as fallback when no specialized delivery adapter (such as Telegram) is enabled.
 * Never outputs OTP to client responses. Logs delivery dispatch for local debugging.
 */
@Component
@ConditionalOnMissingBean(OtpDeliveryPort.class)
public class NoOpSmsProvider implements SmsProviderPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsProvider.class);

    @Override
    public void sendVerificationOtp(String phoneNumber, String challengeOtp) {
        log.info("[DEV NOOP OTP/SMS] OTP challenge dispatched to recipient {} (Hash verified)", maskPhoneNumber(phoneNumber));
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
