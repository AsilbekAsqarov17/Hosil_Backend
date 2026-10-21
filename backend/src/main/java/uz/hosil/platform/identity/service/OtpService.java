package uz.hosil.platform.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.InvalidOtpException;
import uz.hosil.platform.common.error.OtpChallengeLockedException;
import uz.hosil.platform.common.error.RateLimitExceededException;
import uz.hosil.platform.common.error.TelegramLinkRequiredException;
import uz.hosil.platform.common.security.OtpDeliveryPort;
import uz.hosil.platform.identity.domain.OtpChallengeEntity;
import uz.hosil.platform.identity.repository.OtpChallengeRepository;
import uz.hosil.platform.identity.telegram.TelegramLinkService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages phone number verification OTP challenges, cryptographic hashing, rate limits, and verification.
 * Employs server-secret-keyed HMAC-SHA256 to protect against offline brute-force dictionary attacks.
 * Source of Truth: docs/04_domain_model.md §4.1 and docs/05_api_contract.md §3.1.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int OTP_EXPIRY_SECONDS = 180; // 3 minutes per contract
    private static final int RESEND_AFTER_SECONDS = 60; // 60 seconds cooldown
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 300; // 5 minutes
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpDeliveryPort otpDeliveryPort;
    private final TelegramLinkService telegramLinkService;
    private final String deliveryProvider;
    private final byte[] secretKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public OtpService(
            OtpChallengeRepository otpChallengeRepository,
            OtpDeliveryPort otpDeliveryPort,
            TelegramLinkService telegramLinkService,
            @Value("${hosil.otp.delivery.provider:${hosil.sms.provider:NOOP}}") String deliveryProvider,
            @Value("${hosil.security.otp.secret:dev_otp_hmac_secret_must_be_at_least_256_bits_long_local_only}") String otpSecret
    ) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.otpDeliveryPort = otpDeliveryPort;
        this.telegramLinkService = telegramLinkService;
        this.deliveryProvider = deliveryProvider != null ? deliveryProvider.trim() : "NOOP";
        this.secretKeyBytes = deriveKeyBytes(otpSecret);
    }

    public OtpService(
            OtpChallengeRepository otpChallengeRepository,
            OtpDeliveryPort otpDeliveryPort,
            String otpSecret
    ) {
        this(otpChallengeRepository, otpDeliveryPort, null, "NOOP", otpSecret);
    }

    private byte[] deriveKeyBytes(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public record OtpChallengeResult(
            UUID challengeId,
            int expiresInSeconds,
            int resendAfterSeconds
    ) {}

    @Transactional
    public OtpChallengeResult requestOtpChallenge(String phoneNumber) {
        // Pre-condition check: if Telegram delivery is active, verify phone has linked chat per Milestone 6C.5
        if ("TELEGRAM".equalsIgnoreCase(deliveryProvider) && telegramLinkService != null) {
            if (!telegramLinkService.isPhoneLinked(phoneNumber)) {
                log.info("Telegram link required for phone {}", maskPhoneNumber(phoneNumber));
                throw new TelegramLinkRequiredException("Telegram orqali tasdiqlash talab etiladi. Avval Telegram bot orqali raqamingizni bogʻlang.");
            }
        }

        Instant now = Instant.now();
        List<OtpChallengeEntity> existingChallenges = otpChallengeRepository
                .findByPhoneNumberOrderByCreatedAtDesc(phoneNumber);

        // 1. Enforce 60-second cooldown between consecutive OTP requests per DEC-003 and docs/05_api_contract.md §3.1
        if (!existingChallenges.isEmpty()) {
            OtpChallengeEntity mostRecent = existingChallenges.get(0);
            if (mostRecent.getCreatedAt() != null) {
                long elapsed = Duration.between(mostRecent.getCreatedAt(), now).getSeconds();
                if (elapsed < RESEND_AFTER_SECONDS) {
                    int retryAfter = (int) Math.max(1, RESEND_AFTER_SECONDS - elapsed);
                    log.warn("OTP resend cooldown active for {}: {}s remaining", maskPhoneNumber(phoneNumber), retryAfter);
                    throw new RateLimitExceededException("Kodni qayta yuborish uchun biroz kuting.", retryAfter);
                }
            }
        }

        // 2. Enforce sliding rate limit window (max 3 challenges per 5 minutes)
        Instant rateLimitWindowStart = now.minusSeconds(RATE_LIMIT_WINDOW_SECONDS);
        long recentChallengesCount = otpChallengeRepository.countByPhoneNumberAndCreatedAtAfter(
                phoneNumber, rateLimitWindowStart
        );

        if (recentChallengesCount >= MAX_REQUESTS_PER_WINDOW) {
            int retryAfter = RATE_LIMIT_WINDOW_SECONDS;
            if (!existingChallenges.isEmpty()) {
                OtpChallengeEntity oldestInWindow = existingChallenges.get(Math.min((int) recentChallengesCount - 1, existingChallenges.size() - 1));
                long elapsed = Duration.between(oldestInWindow.getCreatedAt(), now).getSeconds();
                retryAfter = (int) Math.max(1, RATE_LIMIT_WINDOW_SECONDS - elapsed);
            }
            log.warn("Rate limit exceeded for phone number: {}", maskPhoneNumber(phoneNumber));
            throw new RateLimitExceededException("OTP soʻrovlari limiti oshib ketdi. Iltimos, keyinroq urinib koʻring.", retryAfter);
        }

        // Generate 6-digit passcode
        int numericCode = 100000 + secureRandom.nextInt(900000);
        String plainOtp = String.valueOf(numericCode);

        // Generate cryptographic salt and server-secret-keyed HMAC-SHA256 hash
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        String challengeHash = computeHmacSha256(salt, plainOtp);

        Instant expiresAt = now.plusSeconds(OTP_EXPIRY_SECONDS);

        OtpChallengeEntity challenge = new OtpChallengeEntity(phoneNumber, challengeHash, salt, expiresAt);
        challenge.setMaxAttempts(MAX_FAILED_ATTEMPTS);
        challenge = otpChallengeRepository.save(challenge);

        // Dispatch via OTP delivery adapter port (never log or expose plaintext OTP)
        otpDeliveryPort.sendVerificationOtp(phoneNumber, plainOtp);

        return new OtpChallengeResult(challenge.getId(), OTP_EXPIRY_SECONDS, RESEND_AFTER_SECONDS);
    }

    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpChallengeLockedException.class})
    public String verifyOtpChallenge(UUID challengeId, String submittedOtp) {
        OtpChallengeEntity challenge = otpChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new InvalidOtpException("Berilgan OTP soʻrovi topilmadi"));

        if (challenge.isConsumed()) {
            throw new InvalidOtpException("Ushbu OTP kodi allaqachon tasdiqlangan yoki ishlatilgan");
        }

        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP kodining amal qilish muddati tugagan");
        }

        if (challenge.getAttemptsCount() >= challenge.getMaxAttempts()) {
            throw new OtpChallengeLockedException("Ushbu OTP soʻrovi xavfsizlik nuqtai nazaridan bloklandi");
        }

        // Primary verification: server-secret-keyed HMAC-SHA256
        String computedHmac = computeHmacSha256(challenge.getSalt(), submittedOtp);
        boolean matches = MessageDigest.isEqual(
                computedHmac.getBytes(StandardCharsets.UTF_8),
                challenge.getChallengeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            // Backward-compatible verification: plain SHA-256 for challenges issued before HMAC upgrade
            String legacyHash = computeLegacySha256(challenge.getSalt(), submittedOtp);
            matches = MessageDigest.isEqual(
                    legacyHash.getBytes(StandardCharsets.UTF_8),
                    challenge.getChallengeHash().getBytes(StandardCharsets.UTF_8)
            );
        }

        if (!matches) {
            int newAttempts = challenge.getAttemptsCount() + 1;
            challenge.setAttemptsCount(newAttempts);
            otpChallengeRepository.save(challenge);

            if (newAttempts >= challenge.getMaxAttempts()) {
                throw new OtpChallengeLockedException("OTP kodi 5 marta xato kiritildi va soʻrov bloklandi");
            }
            int remaining = challenge.getMaxAttempts() - newAttempts;
            throw new InvalidOtpException("Kiritilgan OTP kodi notoʻgʻri. Qolgan urinishlar: " + remaining);
        }

        // Verification successful
        challenge.setVerified(true);
        challenge.setConsumed(true);
        challenge.setVerifiedAt(Instant.now());
        otpChallengeRepository.save(challenge);

        return challenge.getPhoneNumber();
    }

    private String computeHmacSha256(String salt, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
            mac.init(keySpec);
            String payload = (salt != null ? salt : "") + ":" + code;
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 calculation failed", e);
        }
    }

    private String computeLegacySha256(String salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (salt != null ? salt : "") + ":" + code;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
