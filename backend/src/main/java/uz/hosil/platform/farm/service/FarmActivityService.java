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
import uz.hosil.platform.farm.domain.FarmActivityEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmActivityRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.web.ActivityBatchSyncRequest;
import uz.hosil.platform.farm.web.ActivityBatchSyncResponse;
import uz.hosil.platform.farm.web.ActivityMutationItem;
import uz.hosil.platform.farm.web.ActivitySyncResultItem;
import uz.hosil.platform.farm.web.CreateFarmActivityRequest;
import uz.hosil.platform.farm.web.FarmActivityDto;
import uz.hosil.platform.farm.web.PageEnvelope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing daily field activities, idempotent replay ingestion,
 * and offline batch synchronization.
 */
@Service
public class FarmActivityService {

    private static final Logger log = LoggerFactory.getLogger(FarmActivityService.class);
    public static final int MAX_BATCH_SIZE = 50;

    private final FarmActivityRepository activityRepository;
    private final FieldRepository fieldRepository;
    private final CropCycleRepository cropCycleRepository;
    private final FarmAuthorizationService authService;

    public FarmActivityService(FarmActivityRepository activityRepository,
                               FieldRepository fieldRepository,
                               CropCycleRepository cropCycleRepository,
                               FarmAuthorizationService authService) {
        this.activityRepository = activityRepository;
        this.fieldRepository = fieldRepository;
        this.cropCycleRepository = cropCycleRepository;
        this.authService = authService;
    }

    public record ActivityCreationResult(FarmActivityDto dto, boolean created) {}

    @Transactional
    public ActivityCreationResult createActivity(UUID farmId, CreateFarmActivityRequest req, UUID userId) {
        authService.requireActivityWritePermission(farmId, userId);

        // Idempotency check: client-generated UUID or clientMutationId already processed
        if (req.id() != null) {
            Optional<FarmActivityEntity> existing = activityRepository.findByIdAndFarmId(req.id(), farmId);
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for activity ID: {}", req.id());
                return new ActivityCreationResult(FarmActivityDto.fromEntity(existing.get()), false);
            }
        }
        if (req.clientMutationId() != null && !req.clientMutationId().isBlank()) {
            Optional<FarmActivityEntity> existing = activityRepository.findByFarmIdAndClientMutationId(farmId, req.clientMutationId());
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for clientMutationId: {}", req.clientMutationId());
                return new ActivityCreationResult(FarmActivityDto.fromEntity(existing.get()), false);
            }
        }

        // Validate field ownership
        if (!fieldRepository.existsByIdAndFarmId(req.fieldId(), farmId)) {
            throw new ResourceNotFoundException("Field not found on specified farm: " + req.fieldId());
        }

        // Validate crop cycle if specified
        if (req.cropCycleId() != null) {
            CropCycleEntity cropCycle = cropCycleRepository.findByIdAndFarmId(req.cropCycleId(), farmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Crop cycle not found on farm: " + req.cropCycleId()));
            if (!cropCycle.getFieldId().equals(req.fieldId())) {
                throw new BusinessConflictException("Crop cycle does not belong to specified field");
            }
        }

        BigDecimal quantity = null;
        if (req.quantity() != null && !req.quantity().isBlank()) {
            quantity = new BigDecimal(req.quantity());
            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessConflictException("Quantity must be non-negative");
            }
        }

        Long version = req.syncVersion() != null && req.syncVersion() > 0 ? req.syncVersion() : 1L;

        FarmActivityEntity entity = new FarmActivityEntity(
                req.id(),
                farmId,
                req.cropCycleId(),
                req.fieldId(),
                userId,
                req.activityType(),
                req.activityTimestamp() != null ? req.activityTimestamp() : Instant.now(),
                quantity,
                req.unit(),
                req.notes(),
                version,
                req.clientMutationId()
        );

        entity = activityRepository.save(entity);
        return new ActivityCreationResult(FarmActivityDto.fromEntity(entity), true);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<FarmActivityDto> listActivities(UUID farmId, UUID cropCycleId, UUID fieldId, UUID userId, Pageable pageable) {
        authService.requireMembership(farmId, userId);

        Page<FarmActivityEntity> page;
        if (cropCycleId != null && fieldId != null) {
            page = activityRepository.findByFarmIdAndFieldIdAndCropCycleId(farmId, fieldId, cropCycleId, pageable);
        } else if (cropCycleId != null) {
            page = activityRepository.findByFarmIdAndCropCycleId(farmId, cropCycleId, pageable);
        } else if (fieldId != null) {
            page = activityRepository.findByFarmIdAndFieldId(farmId, fieldId, pageable);
        } else {
            page = activityRepository.findByFarmId(farmId, pageable);
        }

        return PageEnvelope.from(page.map(FarmActivityDto::fromEntity));
    }

    @Transactional
    public ActivityBatchSyncResponse batchSyncActivities(UUID farmId, ActivityBatchSyncRequest batchReq, UUID userId) {
        authService.requireActivityWritePermission(farmId, userId);

        if (batchReq.mutations().size() > MAX_BATCH_SIZE) {
            throw new BusinessConflictException("Batch size exceeds maximum limit of " + MAX_BATCH_SIZE + " mutations");
        }

        Instant processedAt = Instant.now();
        List<ActivitySyncResultItem> results = new ArrayList<>();

        for (ActivityMutationItem mutation : batchReq.mutations()) {
            try {
                ActivitySyncResultItem itemResult = processSingleMutation(farmId, mutation, userId);
                results.add(itemResult);
            } catch (Exception ex) {
                log.warn("Error processing mutation {}: {}", mutation.clientMutationId(), ex.getMessage());
                results.add(new ActivitySyncResultItem(
                        mutation.clientMutationId(),
                        mutation.entityId(),
                        "REJECTED",
                        0L,
                        processedAt,
                        ex.getMessage()
                ));
            }
        }

        return new ActivityBatchSyncResponse(batchReq.clientBatchSyncId(), processedAt, results);
    }

    private ActivitySyncResultItem processSingleMutation(UUID farmId, ActivityMutationItem mutation, UUID userId) {
        String action = mutation.action().toUpperCase();
        UUID entityId = mutation.entityId();
        String mutationId = mutation.clientMutationId();

        if ("CREATE".equals(action)) {
            // Check idempotent duplicate by entityId or clientMutationId
            Optional<FarmActivityEntity> byEntityId = activityRepository.findByIdAndFarmId(entityId, farmId);
            if (byEntityId.isPresent()) {
                FarmActivityEntity existing = byEntityId.get();
                return new ActivitySyncResultItem(
                        mutationId,
                        existing.getId(),
                        "SUCCESS",
                        existing.getSyncVersion(),
                        existing.getUpdatedAt(),
                        "Already processed (idempotent replay)"
                );
            }

            Optional<FarmActivityEntity> byMutationId = activityRepository.findByFarmIdAndClientMutationId(farmId, mutationId);
            if (byMutationId.isPresent()) {
                FarmActivityEntity existing = byMutationId.get();
                return new ActivitySyncResultItem(
                        mutationId,
                        existing.getId(),
                        "SUCCESS",
                        existing.getSyncVersion(),
                        existing.getUpdatedAt(),
                        "Already processed by clientMutationId"
                );
            }

            CreateFarmActivityRequest p = mutation.payload();
            if (!fieldRepository.existsByIdAndFarmId(p.fieldId(), farmId)) {
                return new ActivitySyncResultItem(
                        mutationId,
                        entityId,
                        "REJECTED",
                        0L,
                        Instant.now(),
                        "Field does not exist on farm: " + p.fieldId()
                );
            }

            if (p.cropCycleId() != null) {
                Optional<CropCycleEntity> cycleOpt = cropCycleRepository.findByIdAndFarmId(p.cropCycleId(), farmId);
                if (cycleOpt.isEmpty() || !cycleOpt.get().getFieldId().equals(p.fieldId())) {
                    return new ActivitySyncResultItem(
                            mutationId,
                            entityId,
                            "REJECTED",
                            0L,
                            Instant.now(),
                            "Invalid crop cycle or does not belong to field"
                    );
                }
            }

            BigDecimal quantity = null;
            if (p.quantity() != null && !p.quantity().isBlank()) {
                quantity = new BigDecimal(p.quantity());
                if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                    return new ActivitySyncResultItem(
                            mutationId,
                            entityId,
                            "REJECTED",
                            0L,
                            Instant.now(),
                            "Quantity must be non-negative"
                    );
                }
            }

            Long version = mutation.clientSyncVersion() != null && mutation.clientSyncVersion() > 0
                    ? mutation.clientSyncVersion()
                    : 1L;

            FarmActivityEntity entity = new FarmActivityEntity(
                    entityId,
                    farmId,
                    p.cropCycleId(),
                    p.fieldId(),
                    userId,
                    p.activityType(),
                    p.activityTimestamp() != null ? p.activityTimestamp() : Instant.now(),
                    quantity,
                    p.unit(),
                    p.notes(),
                    version,
                    mutationId
            );

            entity = activityRepository.save(entity);
            return new ActivitySyncResultItem(
                    mutationId,
                    entity.getId(),
                    "CREATED",
                    entity.getSyncVersion(),
                    entity.getUpdatedAt(),
                    "Successfully synchronized"
            );

        } else if ("UPDATE".equals(action)) {
            Optional<FarmActivityEntity> opt = activityRepository.findByIdAndFarmId(entityId, farmId);
            if (opt.isEmpty()) {
                return new ActivitySyncResultItem(
                        mutationId,
                        entityId,
                        "REJECTED",
                        0L,
                        Instant.now(),
                        "Activity not found to update"
                );
            }

            FarmActivityEntity existing = opt.get();

            // Optimistic concurrency conflict detection: clientSyncVersion must match server's current syncVersion
            if (mutation.clientSyncVersion() == null || !existing.getSyncVersion().equals(mutation.clientSyncVersion())) {
                log.warn("Sync conflict on activity {}: server version={}, client base version={}",
                        entityId, existing.getSyncVersion(), mutation.clientSyncVersion());
                return new ActivitySyncResultItem(
                        mutationId,
                        entityId,
                        "CONFLICT",
                        existing.getSyncVersion(),
                        existing.getUpdatedAt(),
                        "Version conflict: server is at version " + existing.getSyncVersion() + ", client base was " + mutation.clientSyncVersion()
                );
            }

            CreateFarmActivityRequest p = mutation.payload();
            if (p != null) {
                if (p.activityType() != null && !p.activityType().isBlank()) {
                    existing.setActivityType(p.activityType());
                }
                if (p.quantity() != null && !p.quantity().isBlank()) {
                    BigDecimal q = new BigDecimal(p.quantity());
                    if (q.compareTo(BigDecimal.ZERO) < 0) {
                        return new ActivitySyncResultItem(
                                mutationId,
                                entityId,
                                "REJECTED",
                                existing.getSyncVersion(),
                                existing.getUpdatedAt(),
                                "Quantity must be non-negative"
                        );
                    }
                    existing.setQuantity(q);
                }
                if (p.unit() != null) {
                    existing.setUnit(p.unit());
                }
                if (p.notes() != null) {
                    existing.setNotes(p.notes());
                }
                if (p.activityTimestamp() != null) {
                    existing.setActivityTimestamp(p.activityTimestamp());
                }
            }

            existing.setSyncVersion(existing.getSyncVersion() + 1L);
            existing = activityRepository.save(existing);

            return new ActivitySyncResultItem(
                    mutationId,
                    existing.getId(),
                    "UPDATED",
                    existing.getSyncVersion(),
                    existing.getUpdatedAt(),
                    "Successfully updated"
            );
        } else {
            return new ActivitySyncResultItem(
                    mutationId,
                    entityId,
                    "REJECTED",
                    0L,
                    Instant.now(),
                    "Unsupported action: " + action
            );
        }
    }
}
