package uz.hosil.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generates and validates cryptographic RFC 7519 JWT tokens for API clients.
 * Stateless authentication provider per DEC-003 and docs/02_architecture.md §5.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${hosil.security.jwt.secret:dev_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256_local_only}") String secret,
            @Value("${hosil.security.jwt.expiration-seconds:900}") long expirationSeconds
    ) {
        this.secretKey = deriveHmacSha256Key(secret);
        this.expirationSeconds = expirationSeconds;
    }

    private SecretKey deriveHmacSha256Key(String secret) {
        try {
            // Ensure 256 bits (32 bytes) minimum for HMAC-SHA256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }

    public String generateAccessToken(UUID userId, String phoneNumber, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("phoneNumber", phoneNumber)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {
        String subject = getClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    public String getPhoneNumber(String token) {
        return getClaims(token).get("phoneNumber", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return getClaims(token).get("roles", List.class);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
