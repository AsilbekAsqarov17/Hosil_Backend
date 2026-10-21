package uz.hosil.platform.farm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public summary exposed to other modules when a harvest batch is locked and allocated.
 */
public record AllocatedHarvestBatchInfo(
        UUID batchId,
        UUID farmId,
        String cropType,
        String qualityGrade,
        BigDecimal allocatedQuantity,
        BigDecimal remainingUnallocatedQuantity,
        String unitCode,
        String region,
        String district
) {}
