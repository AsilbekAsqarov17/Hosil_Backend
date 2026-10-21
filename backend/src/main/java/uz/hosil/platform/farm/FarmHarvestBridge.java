package uz.hosil.platform.farm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public cross-module bridge for atomically locking and allocating physical harvest stock.
 */
public interface FarmHarvestBridge {

    /**
     * Atomically locks harvest batch with PESSIMISTIC_WRITE, validates caller permissions
     * and quantity availability, subtracts the allocated quantity, and updates batch status.
     *
     * @param harvestBatchId   the physical harvest batch identifier
     * @param requestedQuantity quantity to allocate
     * @param requestedUnitCode measurement unit code
     * @param callerUserId      authenticated caller requesting publication
     * @return summary of allocated batch and owning farm location
     */
    AllocatedHarvestBatchInfo lockAndAllocateHarvestBatch(
            UUID harvestBatchId,
            BigDecimal requestedQuantity,
            String requestedUnitCode,
            UUID callerUserId
    );

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the harvest batch
     * strictly following the lock acquisition hierarchy (1. harvest_batches, 2. harvest_listings).
     *
     * @param harvestBatchId the physical harvest batch identifier
     */
    void lockHarvestBatch(UUID harvestBatchId);

    /**
     * Looks up harvest batch and owning farm summary for transport request linking.
     *
     * @param harvestBatchId the harvest batch ID
     * @return summary of the harvest batch if found
     */
    java.util.Optional<HarvestBatchSummary> findHarvestBatchSummary(UUID harvestBatchId);
}

