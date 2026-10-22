package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.LeasingApplicationEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LeasingApplicationDto(
        UUID id,
        String applicationNumber,
        UUID applicantUserId,
        UUID equipmentId,
        UUID farmId,
        String applicantBusinessType,
        String applicantName,
        String applicantPhone,
        Integer desiredTermMonths,
        BigDecimal proposedDownPayment,
        BigDecimal estimatedEquipmentCost,
        String currency,
        String intendedUseNotes,
        String status,
        String partnerBankCode,
        String partnerApplicationRef,
        BigDecimal approvedPrincipal,
        Integer approvedTermMonths,
        BigDecimal approvedDownPayment,
        BigDecimal approvedMonthlyInstallment,
        String rejectionReason,
        String reviewNotes,
        Boolean isSimulated,
        String disclaimer,
        String idempotencyKey,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeasingApplicationDto fromEntity(LeasingApplicationEntity a) {
        return new LeasingApplicationDto(
                a.getId(),
                a.getApplicationNumber(),
                a.getApplicantUserId(),
                a.getEquipmentId(),
                a.getFarmId(),
                a.getApplicantBusinessType(),
                a.getApplicantName(),
                a.getApplicantPhone(),
                a.getDesiredTermMonths(),
                a.getProposedDownPayment(),
                a.getEstimatedEquipmentCost(),
                a.getCurrency(),
                a.getIntendedUseNotes(),
                a.getStatus(),
                a.getPartnerBankCode(),
                a.getPartnerApplicationRef(),
                a.getApprovedPrincipal(),
                a.getApprovedTermMonths(),
                a.getApprovedDownPayment(),
                a.getApprovedMonthlyInstallment(),
                a.getRejectionReason(),
                a.getReviewNotes(),
                a.getIsSimulated(),
                a.getDisclaimer(),
                a.getIdempotencyKey(),
                a.getSubmittedAt(),
                a.getReviewedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
