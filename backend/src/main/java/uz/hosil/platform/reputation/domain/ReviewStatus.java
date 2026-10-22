package uz.hosil.platform.reputation.domain;

/**
 * State machine for reviews per docs/12_reputation_and_reviews.md §5.2.
 */
public enum ReviewStatus {
    BLIND_PENDING,
    PUBLISHED,
    FLAGGED,
    HIDDEN,
    REMOVED
}
