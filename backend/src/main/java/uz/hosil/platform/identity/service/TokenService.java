package uz.hosil.platform.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.InvalidRefreshTokenException;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.identity.domain.AuthSessionEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserRoleEntity;
import uz.hosil.platform.identity.repository.AuthSessionRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.identity.repository.UserRoleRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages JWT access tokens and database-backed refresh tokens in auth_sessions.
 * Enforces token rotation, sliding activity tracking, and token reuse detection.
 * Source of Truth: docs/04_domain_model.md §4.1 and docs/05_api_contract.md §3.1.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(30);

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public TokenService(
            JwtTokenProvider jwtTokenProvider,
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public record TokenPairResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            int expiresInSeconds
    ) {}

    @Transactional
    public TokenPairResult issueTokenPair(
            UUID userId,
            String phoneNumber,
            List<String> roles,
            String deviceFingerprint,
            String userAgent,
            String ipAddress
    ) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, phoneNumber, roles);
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_VALIDITY);
        AuthSessionEntity session = new AuthSessionEntity(
                userId,
                tokenHash,
                expiresAt,
                deviceFingerprint,
                userAgent,
                ipAddress
        );
        authSessionRepository.save(session);

        return new TokenPairResult(
                accessToken,
                rawRefreshToken,
                "Bearer",
                (int) jwtTokenProvider.getExpirationSeconds()
        );
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class})
    public TokenPairResult refreshToken(
            String rawRefreshToken,
            String deviceFingerprint,
            String userAgent,
            String ipAddress
    ) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Yangilash kaliti koʻrsatilmagan");
        }

        String tokenHash = hashToken(rawRefreshToken);
        AuthSessionEntity session = authSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Yangilash kaliti topilmadi"));

        if (session.isRevoked()) {
            // Replay attack / Token reuse detected! Revoke all active sessions for this user.
            log.error("Refresh token reuse detected for user ID {}. Revoking all sessions.", session.getUserId());
            authSessionRepository.revokeAllByUserId(session.getUserId());
            throw new InvalidRefreshTokenException("Ushbu yangilash kaliti allaqachon bekor qilingan. Xavfsizlik choralari qoʻllandi.");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Yangilash kalitining amal qilish muddati tugagan");
        }

        UserEntity user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Foydalanuvchi hisobi topilmadi"));

        if ("SUSPENDED".equals(user.getStatus())) {
            throw new InvalidRefreshTokenException("Foydalanuvchi hisobi toʻxtatilgan");
        }

        // Rotate: mark old session as revoked
        session.setRevoked(true);
        session.setLastActiveAt(Instant.now());
        authSessionRepository.save(session);

        // Issue new token pair
        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRoleEntity::getRoleName)
                .toList();

        return issueTokenPair(
                user.getId(),
                user.getPhoneNumber(),
                roles,
                deviceFingerprint != null ? deviceFingerprint : session.getDeviceFingerprint(),
                userAgent != null ? userAgent : session.getUserAgent(),
                ipAddress != null ? ipAddress : session.getIpAddress()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            authSessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
                session.setRevoked(true);
                session.setLastActiveAt(Instant.now());
                authSessionRepository.save(session);
                log.info("Session revoked for user ID {}", session.getUserId());
            });
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
