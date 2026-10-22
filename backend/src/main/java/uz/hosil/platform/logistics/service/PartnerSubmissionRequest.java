package uz.hosil.platform.logistics.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Normalized financing submission payload forwarded to partner financial institutions.
 * Strictly adheres to PRD LEASE-002, LEASE-004, and DEC-013.
 */
public record PartnerSubmissionRequest(
        UUID applicationId,
        String applicationNumber,
        UUID equipmentId,
        String equipmentModel,
        BigDecimal estimatedEquipmentCost,
        BigDecimal proposedDownPayment,
        Integer desiredTermMonths,
        String applicantName,
        String applicantPhone,
        String applicantBusinessType,
        String currency
) {
}
