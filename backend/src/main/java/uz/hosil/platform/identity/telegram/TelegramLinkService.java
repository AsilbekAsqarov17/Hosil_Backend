package uz.hosil.platform.identity.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.identity.domain.TelegramLinkingSessionEntity;
import uz.hosil.platform.identity.domain.TelegramProcessedUpdateEntity;
import uz.hosil.platform.identity.domain.TelegramUserLinkEntity;
import uz.hosil.platform.identity.repository.TelegramLinkingSessionRepository;
import uz.hosil.platform.identity.repository.TelegramProcessedUpdateRepository;
import uz.hosil.platform.identity.repository.TelegramUserLinkRepository;
import uz.hosil.platform.identity.web.TelegramLinkStatusResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Manages the first-time Telegram contact linking flow, session nonces, and updates
 * adhering to Milestone 6C.5 and DEC-003.
 */
@Service
public class TelegramLinkService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLinkService.class);

    private final TelegramLinkingSessionRepository sessionRepository;
    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramProcessedUpdateRepository processedUpdateRepository;
    private final TelegramApiClient telegramApiClient;
    private final String botUsername;
    private final int linkingTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramLinkService(
            TelegramLinkingSessionRepository sessionRepository,
            TelegramUserLinkRepository userLinkRepository,
            TelegramProcessedUpdateRepository processedUpdateRepository,
            TelegramApiClient telegramApiClient,
            @Value("${hosil.telegram.bot-username:hosil_authentication_bot}") String botUsername,
            @Value("${hosil.security.otp.linking-ttl-seconds:600}") int linkingTtlSeconds
    ) {
        this.sessionRepository = sessionRepository;
        this.userLinkRepository = userLinkRepository;
        this.processedUpdateRepository = processedUpdateRepository;
        this.telegramApiClient = telegramApiClient;
        this.botUsername = cleanBotUsername(botUsername);
        this.linkingTtlSeconds = linkingTtlSeconds > 0 ? linkingTtlSeconds : 600;
    }

    private String cleanBotUsername(String username) {
        if (username == null || username.isBlank()) {
            return "hosil_authentication_bot";
        }
        return username.trim().replaceAll("^@", "");
    }

    public record TelegramLinkSessionDto(
            String token,
            String deepLink,
            String botUsername,
            int expiresInSeconds
    ) {}

    @Transactional
    public TelegramLinkSessionDto createLinkingSession(String rawPhoneNumber) {
        String canonicalPhone = normalizePhoneNumber(rawPhoneNumber);
        if (!canonicalPhone.matches("^(\\+998)[0-9]{9}$")) {
            throw new ValidationException("Telefon raqami +998XXXXXXXXX formatida boʻlishi kerak");
        }

        // Generate 32-byte cryptographic token suitable for Telegram start parameter
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String nonceHash = computeSha256(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(linkingTtlSeconds);

        TelegramLinkingSessionEntity session = new TelegramLinkingSessionEntity(
                canonicalPhone,
                nonceHash,
                expiresAt
        );
        sessionRepository.save(session);

        String deepLink = "https://t.me/" + botUsername + "?start=" + rawToken;
        log.info("Created Telegram linking session for phone {} (TTL: {}s)", maskPhoneNumber(canonicalPhone), linkingTtlSeconds);

        return new TelegramLinkSessionDto(rawToken, deepLink, botUsername, linkingTtlSeconds);
    }

    @Transactional(readOnly = true)
    public boolean isPhoneLinked(String rawPhoneNumber) {
        String canonicalPhone = normalizePhoneNumber(rawPhoneNumber);
        return userLinkRepository.existsByPhoneNumber(canonicalPhone);
    }

    @Transactional(readOnly = true)
    public TelegramLinkStatusResponse checkLinkingStatusBySessionToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("Sessiya tokeni koʻrsatilishi shart");
        }
        String nonceHash = computeSha256(rawToken.trim());
        TelegramLinkingSessionEntity session = sessionRepository.findByNonceHash(nonceHash)
                .orElseThrow(() -> new ValidationException("Yaroqsiz yoki topilmagan sessiya tokeni"));

        if (!session.isConsumed() && session.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Tasdiqlash sessiyasining amal qilish muddati tugagan");
        }

        boolean isLinked = session.isConsumed() || userLinkRepository.existsByPhoneNumber(session.getPhoneNumber());
        return new TelegramLinkStatusResponse(isLinked);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramUserLinkEntity> getLinkedUser(String rawPhoneNumber) {
        String canonicalPhone = normalizePhoneNumber(rawPhoneNumber);
        return userLinkRepository.findByPhoneNumber(canonicalPhone);
    }

    @Transactional
    public void processUpdate(TelegramModels.TelegramUpdate update) {
        if (update == null || update.updateId() == null) {
            return;
        }

        // 1. Enforce idempotency: prevent replaying already processed updates
        if (processedUpdateRepository.existsById(update.updateId())) {
            log.debug("Telegram update ID {} already processed, skipping", update.updateId());
            return;
        }
        processedUpdateRepository.save(new TelegramProcessedUpdateEntity(update.updateId()));

        TelegramModels.TelegramMessage message = update.message();
        if (message == null || message.chat() == null) {
            return;
        }

        // 2. Enforce private chat rule: ignore non-private updates
        if (!"private".equalsIgnoreCase(message.chat().type())) {
            log.warn("Ignored update from non-private chat type: {}", message.chat().type());
            return;
        }

        Long chatId = message.chat().id();
        TelegramModels.TelegramUser from = message.from();
        if (from == null || from.id() == null) {
            return;
        }

        // 3. Handle contact sharing (Verification & Linking)
        if (message.contact() != null) {
            handleContactSharing(message, chatId, from);
            return;
        }

        // 4. Handle text commands (e.g. /start or deep link start)
        if (message.text() != null && message.text().startsWith("/start")) {
            handleStartCommand(message.text(), chatId, from);
        }
    }

    private void handleStartCommand(String text, Long chatId, TelegramModels.TelegramUser from) {
        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length > 1 && !parts[1].isBlank()) {
            String rawToken = parts[1].trim();
            String nonceHash = computeSha256(rawToken);

            Optional<TelegramLinkingSessionEntity> sessionOpt = sessionRepository.findByNonceHash(nonceHash);
            Instant now = Instant.now();

            if (sessionOpt.isPresent()) {
                TelegramLinkingSessionEntity session = sessionOpt.get();
                if (!session.isConsumed() && session.getExpiresAt().isAfter(now)) {
                    // Bind session to this user and chat
                    session.setTelegramChatId(chatId);
                    session.setTelegramUserId(from.id());
                    sessionRepository.save(session);

                    var button = new TelegramModels.TelegramKeyboardButton("📱 Telefon raqamimni yuborish", true);
                    var markup = new TelegramModels.TelegramReplyKeyboardMarkup(
                            List.of(List.of(button)),
                            true,
                            true
                    );

                    telegramApiClient.sendMessage(
                            chatId,
                            "Assalomu alaykum! *Hosil* platformasining rasmiy autentifikatsiya botiga xush kelibsiz.\n\n" +
                            "Hisobingizni tasdiqlash va bir martalik kod (OTP) olish uchun quyidagi *«📱 Telefon raqamimni yuborish»* tugmasini bosing.",
                            "Markdown",
                            markup
                    );
                    return;
                }
            }

            telegramApiClient.sendMessage(
                    chatId,
                    "⚠️ Ushbu havola eskirgan yoki allaqachon ishlatilgan. Iltimos, Hosil ilovasiga qaytib yangi havola oling.",
                    "Markdown",
                    new TelegramModels.TelegramReplyKeyboardRemove()
            );
            return;
        }

        // Regular /start without token
        telegramApiClient.sendMessage(
                chatId,
                "Assalomu alaykum! Bu *Hosil* platformasining autentifikatsiya boti.\n\n" +
                "Iltimos, avval Hosil ilovasida (Bozor, Farm yoki Logistika) telefon raqamingizni kiriting va «Telegram orqali tasdiqlash» tugmasini bosing.",
                "Markdown",
                new TelegramModels.TelegramReplyKeyboardRemove()
        );
    }

    private void handleContactSharing(TelegramModels.TelegramMessage message, Long chatId, TelegramModels.TelegramUser from) {
        TelegramModels.TelegramContact contact = message.contact();

        // Security check A: The contact belongs to the person who pressed the button
        if (contact.userId() == null || !contact.userId().equals(from.id())) {
            log.warn("Rejected contact: contact user ID {} does not match sender ID {}", contact.userId(), from.id());
            telegramApiClient.sendMessage(
                    chatId,
                    "❌ Xavfsizlik qoidasi: Iltimos, boshqa kontaktni emas, aynan oʻzingizning telefon raqamingizni «📱 Telefon raqamimni yuborish» tugmasi orqali yuboring.",
                    "Markdown",
                    new TelegramModels.TelegramReplyKeyboardRemove()
            );
            return;
        }

        String normalizedContactPhone = normalizePhoneNumber(contact.phoneNumber());
        Instant now = Instant.now();

        // Security check B: Find pending unconsumed, unexpired session
        Optional<TelegramLinkingSessionEntity> sessionOpt = sessionRepository
                .findFirstByTelegramUserIdAndIsConsumedFalseOrderByCreatedAtDesc(from.id());

        if (sessionOpt.isEmpty()) {
            // Fallback: check by phone number if session wasn't bound during /start
            sessionOpt = sessionRepository
                    .findFirstByPhoneNumberAndIsConsumedFalseOrderByCreatedAtDesc(normalizedContactPhone);
        }

        if (sessionOpt.isEmpty()) {
            telegramApiClient.sendMessage(
                    chatId,
                    "⚠️ Faol tasdiqlash sessiyasi topilmadi. Iltimos, Hosil ilovasida qaytadan urinib koʻring.",
                    "Markdown",
                    new TelegramModels.TelegramReplyKeyboardRemove()
            );
            return;
        }

        TelegramLinkingSessionEntity session = sessionOpt.get();

        if (session.isConsumed() || session.getExpiresAt().isBefore(now)) {
            telegramApiClient.sendMessage(
                    chatId,
                    "⚠️ Tasdiqlash havolasining amal qilish muddati tugagan. Iltimos, Hosil ilovasida qaytadan urinib koʻring.",
                    "Markdown",
                    new TelegramModels.TelegramReplyKeyboardRemove()
            );
            return;
        }

        // Security check C: The received contact phone number normalizes to the session's phone number
        if (!normalizedContactPhone.equals(session.getPhoneNumber())) {
            log.warn("Contact phone {} does not match pending session phone {}",
                    maskPhoneNumber(normalizedContactPhone), maskPhoneNumber(session.getPhoneNumber()));
            telegramApiClient.sendMessage(
                    chatId,
                    "❌ Yuborilgan telefon raqami Hosil ilovasida kiritilgan raqamga mos kelmadi. Iltimos, ilovada koʻrsatilgan raqamga tegishli kontaktingizni yuboring.",
                    "Markdown",
                    new TelegramModels.TelegramReplyKeyboardRemove()
            );
            return;
        }

        // Verification successful: consume session
        session.setConsumed(true);
        session.setConsumedAt(now);
        sessionRepository.save(session);

        // Persist verified phone-to-chat association
        TelegramUserLinkEntity link = userLinkRepository.findByPhoneNumber(session.getPhoneNumber())
                .orElseGet(() -> new TelegramUserLinkEntity(
                        session.getPhoneNumber(),
                        chatId,
                        from.id(),
                        from.username(),
                        from.firstName(),
                        from.lastName()
                ));

        link.setTelegramChatId(chatId);
        link.setTelegramUserId(from.id());
        link.setTelegramUsername(from.username());
        link.setFirstName(from.firstName());
        link.setLastName(from.lastName());
        link.setUpdatedAt(now);
        userLinkRepository.save(link);

        log.info("Successfully established Telegram link for phone {} with chat ID {}",
                maskPhoneNumber(session.getPhoneNumber()), maskChatId(chatId));

        telegramApiClient.sendMessage(
                chatId,
                "✅ Telefon raqamingiz muvaffaqiyatli tasdiqlandi!\n\n" +
                "Endi Hosil ilovasiga qayting va tasdiqlash kodini soʻrang.",
                "Markdown",
                new TelegramModels.TelegramReplyKeyboardRemove()
        );
    }

    public static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("998") && digits.length() == 12) {
            return "+" + digits;
        }
        if (digits.length() == 9) {
            return "+998" + digits;
        }
        if (phone.startsWith("+") && digits.startsWith("998") && digits.length() == 12) {
            return "+" + digits;
        }
        return phone.trim();
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }

    private String maskChatId(Long chatId) {
        if (chatId == null) return "***";
        String s = chatId.toString();
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }
}
