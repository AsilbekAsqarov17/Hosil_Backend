package uz.hosil.platform.farm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.farm.web.CreateHarvestBatchRequest;
import uz.hosil.platform.farm.web.HarvestBatchDto;
import uz.hosil.platform.farm.web.PageEnvelope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class HarvestBatchService {

    private static final Logger log = LoggerFactory.getLogger(HarvestBatchService.class);

    private static final Set<String> VALID_UNITS = Set.of("KG", "TON", "PIECE", "LITER", "BAG_50KG");
    private static final Set<String> VALID_GRADES = Set.of(
            "PREMIUM", "FIRST_CLASS", "SECOND_CLASS", "STANDARD", "INDUSTRIAL",
            "FIRST_GRADE", "SECOND_GRADE", "PROCESSING"
    );

    private final HarvestBatchRepository harvestBatchRepository;
    private final CropCycleRepository cropCycleRepository;
    private final FarmAuthorizationService authService;

    public HarvestBatchService(
            HarvestBatchRepository harvestBatchRepository,
            CropCycleRepository cropCycleRepository,
            FarmAuthorizationService authService
    ) {
        this.harvestBatchRepository = harvestBatchRepository;
        this.cropCycleRepository = cropCycleRepository;
        this.authService = authService;
    }

    public record HarvestBatchCreationResult(HarvestBatchDto dto, boolean created) {}

    @Transactional
    public HarvestBatchCreationResult createHarvestBatch(
            UUID farmId,
            CreateHarvestBatchRequest req,
            String idempotencyKey,
            UUID callerUserId
    ) {
        authService.requireHarvestRecordPermission(farmId, callerUserId);

        // 1. Idempotency checks
        if (req.id() != null) {
            Optional<HarvestBatchEntity> existing = harvestBatchRepository.findByIdAndFarmId(req.id(), farmId);
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for harvest batch ID: {}", req.id());
                return new HarvestBatchCreationResult(HarvestBatchDto.fromEntity(existing.get()), false);
            }
        }
        if (req.clientBatchId() != null && !req.clientBatchId().isBlank()) {
            Optional<HarvestBatchEntity> existing = harvestBatchRepository.findByFarmIdAndClientBatchId(farmId, req.clientBatchId());
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for clientBatchId: {}", req.clientBatchId());
                return new HarvestBatchCreationResult(HarvestBatchDto.fromEntity(existing.get()), false);
            }
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<HarvestBatchEntity> existing = harvestBatchRepository.findByFarmIdAndIdempotencyKey(farmId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for idempotencyKey: {}", idempotencyKey);
                return new HarvestBatchCreationResult(HarvestBatchDto.fromEntity(existing.get()), false);
            }
        }

        // 2. Validate crop cycle
        CropCycleEntity cropCycle = cropCycleRepository.findByIdAndFarmId(req.cropCycleId(), farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop cycle not found on farm: " + req.cropCycleId()));

        if ("TERMINATED".equalsIgnoreCase(cropCycle.getStatus()) || "CANCELLED".equalsIgnoreCase(cropCycle.getStatus())) {
            throw new BusinessConflictException("Cannot record harvest for terminated crop cycle: " + cropCycle.getId());
        }

        // 3. Validate harvest date
        if (req.harvestDate().isBefore(cropCycle.getPlantingDate())) {
            throw new BusinessConflictException("Harvest date (" + req.harvestDate() + ") cannot be before planting date (" + cropCycle.getPlantingDate() + ")");
        }

        // 4. Validate quantity
        BigDecimal recordedQty;
        try {
            recordedQty = new BigDecimal(req.recordedQuantity());
        } catch (NumberFormatException e) {
            throw new BusinessConflictException("Invalid recordedQuantity format: " + req.recordedQuantity());
        }
        if (recordedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Recorded quantity must be greater than zero: " + req.recordedQuantity());
        }

        // 5. Validate unitCode
        String unit = req.unitCode().toUpperCase();
        if (!VALID_UNITS.contains(unit)) {
            throw new BusinessConflictException("Unsupported unit code: " + req.unitCode() + ". Allowed: " + VALID_UNITS);
        }

        // 6. Validate qualityGrade if present
        String grade = req.qualityGrade();
        if (grade != null && !grade.isBlank()) {
            grade = grade.toUpperCase();
            if (!VALID_GRADES.contains(grade)) {
                throw new BusinessConflictException("Unsupported quality grade: " + req.qualityGrade());
            }
        } else {
            grade = null;
        }

        UUID batchId = req.id() != null ? req.id() : UUID.randomUUID();

        HarvestBatchEntity entity = new HarvestBatchEntity(
                batchId,
                farmId,
                cropCycle.getId(),
                req.cropType(),
                grade,
                recordedQty,
                recordedQty, // unallocated_quantity strictly initialized to recorded_quantity
                unit,
                req.harvestDate(),
                req.storageLocationNotes(),
                "RECORDED",
                req.clientBatchId(),
                idempotencyKey,
                1L
        );

        entity = harvestBatchRepository.save(entity);
        return new HarvestBatchCreationResult(HarvestBatchDto.fromEntity(entity), true);
    }

    @Transactional(readOnly = true)
    public HarvestBatchDto getHarvestBatch(UUID batchId, UUID callerUserId) {
        HarvestBatchEntity batch = harvestBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest batch not found: " + batchId));

        // Privacy check: caller must be a member of the owning farm
        authService.requireMembership(batch.getFarmId(), callerUserId);

        return HarvestBatchDto.fromEntity(batch);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<HarvestBatchDto> listHarvestBatches(UUID farmId, UUID callerUserId, Pageable pageable) {
        authService.requireMembership(farmId, callerUserId);

        Page<HarvestBatchEntity> page = harvestBatchRepository.findByFarmId(farmId, pageable);
        return PageEnvelope.from(page.map(HarvestBatchDto::fromEntity));
    }
}

