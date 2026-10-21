package uz.hosil.platform.identity.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.hosil.platform.common.error.TelegramLinkRequiredException;
import uz.hosil.platform.common.security.OtpDeliveryPort;
import uz.hosil.platform.identity.domain.TelegramUserLinkEntity;
import uz.hosil.platform.identity.repository.TelegramUserLinkRepository;

/**
 * Delivers OTP codes via official Telegram Bot API to a verified Telegram chat per DEC-003.
 * Activated when hosil.otp.delivery.provider=TELEGRAM or hosil.sms.provider=TELEGRAM.
 */
@Component
@ConditionalOnExpression(
    "'${hosil.otp.delivery.provider:${hosil.sms.provider:NOOP}}'.equalsIgnoreCase('TELEGRAM')"
)
public class TelegramOtpDeliveryAdapter implements OtpDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramOtpDeliveryAdapter.class);

    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramApiClient telegramApiClient;

    public TelegramOtpDeliveryAdapter(
            TelegramUserLinkRepository userLinkRepository,
            TelegramApiClient telegramApiClient
    ) {
        this.userLinkRepository = userLinkRepository;
        this.telegramApiClient = telegramApiClient;
    }

    @Override
    public void sendVerificationOtp(String destination, String challengeOtp) {
        String canonicalPhone = TelegramLinkService.normalizePhoneNumber(destination);

        TelegramUserLinkEntity link = userLinkRepository.findByPhoneNumber(canonicalPhone)
                .orElseThrow(() -> new TelegramLinkRequiredException(
                        "Ushbu telefon raqamiga biriktirilgan tasdiqlangan Telegram chat topilmadi. Avval bot orqali bogʻlang."
                ));

        String text = "🔐 *Hosil* tasdiqlash kodi: `" + challengeOtp + "`\n\n" +
                "Ushbu kod 3 daqiqa davomida amal qiladi. Xavfsizlik maqsadida uni hech kimga bermang!";

        telegramApiClient.sendMessage(link.getTelegramChatId(), text, "Markdown", null);
        log.info("Dispatched OTP code via Telegram to verified recipient {}", maskPhoneNumber(canonicalPhone));
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
