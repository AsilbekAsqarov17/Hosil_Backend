package uz.hosil.platform.logistics.web;

import uz.hosil.platform.logistics.domain.LeasingRepaymentScheduleEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RepaymentScheduleItemDto(
        UUID id,
        UUID contractId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal totalInstallment,
        String currency,
        String status,
        Instant paidAt,
        String paymentReference
) {
    public static RepaymentScheduleItemDto fromEntity(LeasingRepaymentScheduleEntity s) {
        return new RepaymentScheduleItemDto(
                s.getId(),
                s.getContractId(),
                s.getInstallmentNumber(),
                s.getDueDate(),
                s.getPrincipalAmount(),
                s.getInterestAmount(),
                s.getTotalInstallment(),
                s.getCurrency(),
                s.getStatus(),
                s.getPaidAt(),
                s.getPaymentReference()
        );
    }
}
