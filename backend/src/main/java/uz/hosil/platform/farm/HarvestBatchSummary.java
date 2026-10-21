package uz.hosil.platform.farm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public summary exposed to other modules when inspecting a harvest batch for transport linking.
 */
public record HarvestBatchSummary(
        UUID batchId,
        UUID farmId,
        UUID farmOwnerUserId,
        String cropType,
        String qualityGrade,
        BigDecimal recordedQuantity,
        BigDecimal unallocatedQuantity,
        String unitCode,
        String region,
        String district
) {}
