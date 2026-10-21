package uz.hosil.platform.common.error;

/**
 * Exception thrown when business validation fails on request payload data.
 * Mapped to HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
