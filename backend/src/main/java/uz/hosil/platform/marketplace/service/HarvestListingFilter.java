package uz.hosil.platform.marketplace.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Filter criteria for public harvest listing discovery on Hosil Bozor.
 */
public record HarvestListingFilter(
        String cropType,
        String region,
        String district,
        String qualityGrade,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean isRfqEnabled
) {
    private static final Set<String> ALLOWED_QUALITY_GRADES = Set.of(
            "PREMIUM", "FIRST_CLASS", "SECOND_CLASS", "STANDARD", "INDUSTRIAL",
            "FIRST_GRADE", "SECOND_GRADE", "PROCESSING"
    );

    public void validate() {
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimal narx manfiy boʻlishi mumkin emas: " + minPrice);
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maksimal narx manfiy boʻlishi mumkin emas: " + maxPrice);
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Minimal narx (" + minPrice + ") maksimal narxdan (" + maxPrice + ") katta boʻlishi mumkin emas");
        }
        if (qualityGrade != null && !qualityGrade.isBlank()) {
            if (!ALLOWED_QUALITY_GRADES.contains(qualityGrade.toUpperCase().trim())) {
                throw new IllegalArgumentException("Nomaʼlum sifat darajasi: " + qualityGrade);
            }
        }
    }
}
