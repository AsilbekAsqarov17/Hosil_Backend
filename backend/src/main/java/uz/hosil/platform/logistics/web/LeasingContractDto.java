package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.LeasingContractEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeasingContractDto(
        UUID id,
        String contractNumber,
        UUID applicationId,
        UUID equipmentId,
        UUID lesseeUserId,
        UUID lessorUserId,
        String partnerBankCode,
        BigDecimal approvedPrincipal,
        BigDecimal approvedDownPayment,
        BigDecimal annualInterestRate,
        Integer termMonths,
        BigDecimal monthlyInstallment,
        BigDecimal totalRepayment,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Instant signedAt,
        Boolean isSimulated,
        String disclaimer,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeasingContractDto fromEntity(LeasingContractEntity c) {
        return new LeasingContractDto(
                c.getId(),
                c.getContractNumber(),
                c.getApplicationId(),
                c.getEquipmentId(),
                c.getLesseeUserId(),
                c.getLessorUserId(),
                c.getPartnerBankCode(),
                c.getApprovedPrincipal(),
                c.getApprovedDownPayment(),
                c.getAnnualInterestRate(),
                c.getTermMonths(),
                c.getMonthlyInstallment(),
                c.getTotalRepayment(),
                c.getCurrency(),
                c.getStartDate(),
                c.getEndDate(),
                c.getStatus(),
                c.getSignedAt(),
                c.getIsSimulated(),
                c.getDisclaimer(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
