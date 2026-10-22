package uz.hosil.platform.logistics.service;

import java.math.BigDecimal;

/**
 * Normalized financing assessment decision returned from partner institution.
 */
public record PartnerDecisionResult(
        String partnerBankCode,
        String partnerApplicationRef,
        String decision, // APPROVED, REJECTED, ADDITIONAL_INFO_REQUESTED
        BigDecimal approvedPrincipal,
        BigDecimal approvedDownPayment,
        BigDecimal annualInterestRate,
        Integer approvedTermMonths,
        BigDecimal approvedMonthlyInstallment,
        String rejectionReason,
        String reviewNotes,
        boolean isSimulated,
        String disclaimer
) {
    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_REJECTED = "REJECTED";
    public static final String DECISION_INFO_REQUESTED = "ADDITIONAL_INFO_REQUESTED";
}
