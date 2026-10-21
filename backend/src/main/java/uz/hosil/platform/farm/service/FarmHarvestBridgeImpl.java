package uz.hosil.platform.farm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.AllocatedHarvestBatchInfo;
import uz.hosil.platform.farm.FarmHarvestBridge;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import uz.hosil.platform.farm.HarvestBatchSummary;

@Service
public class FarmHarvestBridgeImpl implements FarmHarvestBridge {

    private static final Logger log = LoggerFactory.getLogger(FarmHarvestBridgeImpl.class);

    private final HarvestBatchRepository harvestBatchRepository;
    private final FarmRepository farmRepository;
    private final FarmAuthorizationService authService;

    public FarmHarvestBridgeImpl(
            HarvestBatchRepository harvestBatchRepository,
            FarmRepository farmRepository,
            FarmAuthorizationService authService
    ) {
        this.harvestBatchRepository = harvestBatchRepository;
        this.farmRepository = farmRepository;
        this.authService = authService;
    }

    @Override
    @Transactional
    public AllocatedHarvestBatchInfo lockAndAllocateHarvestBatch(
            UUID harvestBatchId,
            BigDecimal requestedQuantity,
            String requestedUnitCode,
            UUID callerUserId
    ) {
        // 1. Acquire pessimistic write lock (SELECT ... FOR UPDATE)
        HarvestBatchEntity batch = harvestBatchRepository.findByIdForUpdate(harvestBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest batch not found: " + harvestBatchId));

        // 2. Authenticate caller and verify PUBLISH_MARKET permission on the owning farm
        authService.requireMarketPublishPermission(batch.getFarmId(), callerUserId);

        // 3. Unit compatibility check
        if (!batch.getUnitCode().equalsIgnoreCase(requestedUnitCode)) {
            throw new BusinessConflictException("Unit code mismatch: batch is logged in '" + batch.getUnitCode()
                    + "' but allocation requested '" + requestedUnitCode + "'");
        }

        // 4. Validate quantity precision & positive
        if (requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Allocated quantity must be greater than zero");
        }

        // 5. Quantity conservation check against currently unallocated quantity
        if (requestedQuantity.compareTo(batch.getUnallocatedQuantity()) > 0) {
            throw new BusinessConflictException("INSUFFICIENT_UNALLOCATED_QUANTITY: Requested "
                    + requestedQuantity + " " + batch.getUnitCode()
                    + " but only " + batch.getUnallocatedQuantity() + " is currently unallocated");
        }

        // 6. Deduct balance safely
        BigDecimal remaining = batch.getUnallocatedQuantity().subtract(requestedQuantity);
        batch.setUnallocatedQuantity(remaining);

        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus("FULLY_ALLOCATED");
        } else {
            batch.setStatus("PARTIALLY_ALLOCATED");
        }
        batch.setUpdatedAt(Instant.now());
        harvestBatchRepository.save(batch);

        FarmEntity farm = farmRepository.findById(batch.getFarmId())
                .orElseThrow(() -> new ResourceNotFoundException("Owning farm not found: " + batch.getFarmId()));

        log.info("Harvest batch {} locked and allocated {} {}. Remaining unallocated: {}",
                batch.getId(), requestedQuantity, batch.getUnitCode(), remaining);

        return new AllocatedHarvestBatchInfo(
                batch.getId(),
                batch.getFarmId(),
                batch.getCropType(),
                batch.getQualityGrade(),
                requestedQuantity,
                remaining,
                batch.getUnitCode(),
                farm.getRegion(),
                farm.getDistrict()
        );
    }

    @Override
    @Transactional
    public void lockHarvestBatch(UUID harvestBatchId) {
        if (harvestBatchId != null) {
            harvestBatchRepository.findByIdForUpdate(harvestBatchId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HarvestBatchSummary> findHarvestBatchSummary(UUID harvestBatchId) {
        if (harvestBatchId == null) {
            return Optional.empty();
        }
        return harvestBatchRepository.findById(harvestBatchId)
                .flatMap(batch -> farmRepository.findById(batch.getFarmId())
                        .map(farm -> new HarvestBatchSummary(
                                batch.getId(),
                                batch.getFarmId(),
                                farm.getOwnerUserId(),
                                batch.getCropType(),
                                batch.getQualityGrade(),
                                batch.getRecordedQuantity(),
                                batch.getUnallocatedQuantity(),
                                batch.getUnitCode(),
                                farm.getRegion(),
                                farm.getDistrict()
                        )));
    }
}

