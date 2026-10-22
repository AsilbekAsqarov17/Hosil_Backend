package uz.hosil.platform.common.error;

/**
 * Thrown when review commentary or rebuttal violates profanity, PII, or content policy.
 * Mapped to HTTP 422 Unprocessable Entity per docs/12_reputation_and_reviews.md §8.
 */
public class ContentPolicyViolationException extends RuntimeException {
    public ContentPolicyViolationException(String message) {
        super(message);
    }
}
