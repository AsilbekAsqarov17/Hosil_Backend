package uz.hosil.platform.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.hosil.platform.identity.service.AuthService;

/**
 * REST controller for Hosil identity and authentication endpoints.
 * Source of Truth: docs/05_api_contract.md §3.1 and OpenAPI docs/api/openapi-v1.yaml.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpChallengeResponse> requestOtp(@Valid @RequestBody OtpRequest request) {
        OtpChallengeResponse response = authService.requestOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/telegram/link-session")
    public ResponseEntity<TelegramLinkSessionResponse> createTelegramLinkSession(
            @Valid @RequestBody TelegramLinkSessionRequest request
    ) {
        TelegramLinkSessionResponse response = authService.createTelegramLinkSession(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/telegram/status")
    public ResponseEntity<TelegramLinkStatusResponse> checkTelegramLinkStatus(
            @Valid @RequestBody TelegramLinkStatusRequest request
    ) {
        TelegramLinkStatusResponse response = authService.checkTelegramLinkStatus(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthTokenResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = extractClientIp(httpRequest);
        AuthTokenResponse response = authService.verifyOtp(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<AuthTokenResponse> refreshToken(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = extractClientIp(httpRequest);
        AuthTokenResponse response = authService.refreshToken(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        // Convert IPv6 localhost to IPv4 localhost for clean INET column representation
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }
}
