package uz.hosil.platform.farm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.web.CreateCropCycleRequest;
import uz.hosil.platform.farm.web.CropCycleDto;
import uz.hosil.platform.farm.web.PageEnvelope;
import uz.hosil.platform.farm.web.UpdateCropCycleRequest;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing crop cycles and enforcing agronomic lifecycle state machines.
 */
@Service
public class CropCycleService {

    private static final Set<String> TERMINAL_STATES = Set.of(
            "HARVESTED", "COMPLETED", "TERMINATED", "CANCELLED", "FAILED"
    );

    private final CropCycleRepository cropCycleRepository;
    private final FieldRepository fieldRepository;
    private final FarmAuthorizationService authService;

    public CropCycleService(CropCycleRepository cropCycleRepository,
                            FieldRepository fieldRepository,
                            FarmAuthorizationService authService) {
        this.cropCycleRepository = cropCycleRepository;
        this.fieldRepository = fieldRepository;
        this.authService = authService;
    }

    @Transactional
    public CropCycleDto createCropCycle(UUID farmId, CreateCropCycleRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_OPERATE_AGRONOMY);

        if (!fieldRepository.existsByIdAndFarmId(req.fieldId(), farmId)) {
            throw new ResourceNotFoundException("Field not found on this farm: " + req.fieldId());
        }

        if (req.expectedHarvestDate() != null && req.expectedHarvestDate().isBefore(req.plantingDate())) {
            throw new BusinessConflictException("Kutilayotgan hosil sanasi ekish sanasidan oldin bo'lishi mumkin emas");
        }

        CropCycleEntity entity = new CropCycleEntity(
                req.id(),
                farmId,
                req.fieldId(),
                req.cropType(),
                req.variety(),
                req.plantingDate(),
                req.expectedHarvestDate(),
                "ACTIVE"
        );

        entity = cropCycleRepository.save(entity);
        return CropCycleDto.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public CropCycleDto getCropCycle(UUID farmId, UUID cycleId, UUID userId) {
        authService.requireMembership(farmId, userId);
        CropCycleEntity entity = cropCycleRepository.findByIdAndFarmId(cycleId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop cycle not found: " + cycleId));
        return CropCycleDto.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<CropCycleDto> listCropCycles(UUID farmId, UUID fieldId, String status, UUID userId, Pageable pageable) {
        authService.requireMembership(farmId, userId);

        Page<CropCycleEntity> page;
        if (fieldId != null && status != null && !status.isBlank()) {
            page = cropCycleRepository.findByFarmIdAndFieldIdAndStatus(farmId, fieldId, status, pageable);
        } else if (fieldId != null) {
            page = cropCycleRepository.findByFarmIdAndFieldId(farmId, fieldId, pageable);
        } else if (status != null && !status.isBlank()) {
            page = cropCycleRepository.findByFarmIdAndStatus(farmId, status, pageable);
        } else {
            page = cropCycleRepository.findByFarmId(farmId, pageable);
        }

        return PageEnvelope.from(page.map(CropCycleDto::fromEntity));
    }

    @Transactional
    public CropCycleDto updateCropCycle(UUID farmId, UUID cycleId, UpdateCropCycleRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_OPERATE_AGRONOMY);

        CropCycleEntity cycle = cropCycleRepository.findByIdAndFarmId(cycleId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop cycle not found: " + cycleId));

        if (req.status() != null && !req.status().isBlank()) {
            validateAndTransitionStatus(cycle, req.status().toUpperCase());
        }

        if (req.expectedHarvestDate() != null) {
            if (req.expectedHarvestDate().isBefore(cycle.getPlantingDate())) {
                throw new BusinessConflictException("Kutilayotgan hosil sanasi ekish sanasidan oldin bo'lishi mumkin emas");
            }
            cycle.setExpectedHarvestDate(req.expectedHarvestDate());
        }

        if (req.actualHarvestDate() != null) {
            if (req.actualHarvestDate().isBefore(cycle.getPlantingDate())) {
                throw new BusinessConflictException("Haqiqiy hosil sanasi ekish sanasidan oldin bo'lishi mumkin emas");
            }
            cycle.setActualHarvestDate(req.actualHarvestDate());
        }

        cycle = cropCycleRepository.save(cycle);
        return CropCycleDto.fromEntity(cycle);
    }

    private void validateAndTransitionStatus(CropCycleEntity cycle, String targetStatus) {
        String canonicalTarget = switch (targetStatus) {
            case "COMPLETED" -> "HARVESTED";
            case "CANCELLED", "FAILED" -> "TERMINATED";
            case "PLANNING" -> "PLANNED";
            default -> targetStatus;
        };

        String current = cycle.getStatus();
        if (current.equals(canonicalTarget)) {
            return;
        }

        if (TERMINAL_STATES.contains(current)) {
            throw new BusinessConflictException("Yakunlangan sikl holatini o'zgartirib bo'lmaydi: " + current);
        }

        boolean allowed = switch (current) {
            case "PLANNED", "PLANNING" -> Set.of("ACTIVE", "HARVESTED", "TERMINATED").contains(canonicalTarget);
            case "ACTIVE" -> Set.of("HARVESTED", "TERMINATED").contains(canonicalTarget);
            default -> false;
        };

        if (!allowed) {
            throw new BusinessConflictException("Noto'g'ri holat o'tishi: " + current + " -> " + canonicalTarget);
        }

        cycle.setStatus(canonicalTarget);

        if ("HARVESTED".equals(canonicalTarget) && cycle.getActualHarvestDate() == null) {
            cycle.setActualHarvestDate(LocalDate.now());
        }
    }
}
