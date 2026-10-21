package uz.hosil.platform.marketplace.web;

/**
 * Payload for rejecting or withdrawing an inquiry with optional reason.
 */
public record InquiryDecisionRequest(
        String reason
) {}
