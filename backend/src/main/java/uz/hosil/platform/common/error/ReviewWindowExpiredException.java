package uz.hosil.platform.common.error;

/**
 * Thrown when review or rebuttal submission is attempted after the designated window has closed.
 * Mapped to HTTP 410 Gone per docs/12_reputation_and_reviews.md §8.
 */
public class ReviewWindowExpiredException extends RuntimeException {
    public ReviewWindowExpiredException(String message) {
        super(message);
    }
}
