package uz.hosil.platform.marketplace.web;

/**
 * Request payload for seller/buyer order decision (confirmation, rejection, cancellation).
 */
public record OrderDecisionRequest(
        String reason
) {}
