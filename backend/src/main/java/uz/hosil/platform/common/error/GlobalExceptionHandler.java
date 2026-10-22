package uz.hosil.platform.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized exception handler for REST APIs.
 * Converts domain and validation exceptions into standardized ErrorEnvelope responses.
 */
@RestControllerAdvice(basePackages = "uz.hosil.platform")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} on path {}", ex.getMessage(), request.getRequestURI());
        ErrorEnvelope envelope = ErrorEnvelope.of("RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(BusinessConflictException ex, HttpServletRequest request) {
        log.warn("Business conflict: {} on path {}", ex.getMessage(), request.getRequestURI());
        ErrorEnvelope envelope = ErrorEnvelope.of("CONFLICT", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("INQUIRY_CONCURRENT_MODIFICATION", "Soʻrov boshqa foydalanuvchi tomonidan yangilandi. Iltimos, qayta urinib koʻring.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("FORBIDDEN", "Kirish huquqi yetarli emas", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        log.warn("Validation failure on path {}: {}", request.getRequestURI(), details);
        ErrorEnvelope envelope = ErrorEnvelope.of("VALIDATION_ERROR", "Kiritilgan maʼlumotlar yaroqsiz", details, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleDomainValidation(ValidationException ex, HttpServletRequest request) {
        log.warn("Validation error on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("VALIDATION_ERROR", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed HTTP request body on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("MALFORMED_REQUEST", "Soʻrov matni notoʻgʻri formatda (JSON sintaksis xatosi)", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid request argument on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("INVALID_FILTER_CRITERIA", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidOtp(InvalidOtpException ex, HttpServletRequest request) {
        log.warn("Invalid OTP on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("INVALID_OTP", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(OtpChallengeLockedException.class)
    public ResponseEntity<ErrorEnvelope> handleOtpLocked(OtpChallengeLockedException ex, HttpServletRequest request) {
        log.warn("OTP challenge locked on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("OTP_CHALLENGE_LOCKED", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(envelope);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        log.warn("Invalid refresh token on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("INVALID_REFRESH_TOKEN", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(envelope);
    }

    @ExceptionHandler(TelegramLinkRequiredException.class)
    public ResponseEntity<ErrorEnvelope> handleTelegramLinkRequired(TelegramLinkRequiredException ex, HttpServletRequest request) {
        log.info("Telegram linking required on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(
                "TELEGRAM_LINK_REQUIRED",
                ex.getMessage(),
                List.of("Ushbu telefon raqami Hosil Telegram botiga ulanmagan. Iltimos, Telegram orqali raqamingizni tasdiqlang."),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(envelope);
    }

    @ExceptionHandler(ReviewWindowExpiredException.class)
    public ResponseEntity<ErrorEnvelope> handleReviewWindowExpired(ReviewWindowExpiredException ex, HttpServletRequest request) {
        log.warn("Review window expired on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("REVIEW_WINDOW_EXPIRED", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.GONE).body(envelope);
    }

    @ExceptionHandler(ContentPolicyViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleContentPolicyViolation(ContentPolicyViolationException ex, HttpServletRequest request) {
        log.warn("Content policy violation on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("CONTENT_POLICY_VIOLATION", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(envelope);
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccountSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        log.warn("Suspended account access attempted on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("ACCOUNT_SUSPENDED", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }

    @ExceptionHandler(InvalidPaymentSignatureException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidPaymentSignature(InvalidPaymentSignatureException ex, HttpServletRequest request) {
        log.warn("Invalid payment webhook signature on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("INVALID_PAYMENT_SIGNATURE", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(envelope);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorEnvelope> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of("AUTHENTICATION_REQUIRED", "Avtorizatsiyadan oʻtish talab etiladi", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(envelope);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled platform error on path {}", request.getRequestURI(), ex);
        ErrorEnvelope envelope = ErrorEnvelope.of("INTERNAL_ERROR", "Serverda kutilmagan xatolik yuz berdi", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope);
    }
}
