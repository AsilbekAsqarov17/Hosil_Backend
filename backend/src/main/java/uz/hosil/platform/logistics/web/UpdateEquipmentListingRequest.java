package uz.hosil.platform.logistics.web;

import java.math.BigDecimal;

public record UpdateEquipmentListingRequest(
        String title,
        String description,
        String specifications,
        BigDecimal dailyRate,
        BigDecimal monthlyRate,
        BigDecimal marketValue,
        BigDecimal minDownPaymentPercent,
        String availabilityStatus,
        String publicationStatus
) {
}
