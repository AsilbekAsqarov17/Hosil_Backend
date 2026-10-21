package uz.hosil.platform.farm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.web.CreateFieldRequest;
import uz.hosil.platform.farm.web.FieldDto;
import uz.hosil.platform.farm.web.PageEnvelope;
import uz.hosil.platform.farm.web.UpdateFieldRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service managing field plots (dala) on a farm.
 */
@Service
public class FieldService {

    private final FieldRepository fieldRepository;
    private final FarmAuthorizationService authService;

    public FieldService(FieldRepository fieldRepository, FarmAuthorizationService authService) {
        this.fieldRepository = fieldRepository;
        this.authService = authService;
    }

    @Transactional
    public FieldDto createField(UUID farmId, CreateFieldRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_OPERATE_AGRONOMY);

        BigDecimal area = new BigDecimal(req.area());
        FieldEntity field = new FieldEntity(
                req.id(),
                farmId,
                req.name(),
                area,
                req.areaUnit(),
                req.boundaryGeojson(),
                req.notes()
        );
        field = fieldRepository.save(field);
        return FieldDto.fromEntity(field);
    }

    @Transactional(readOnly = true)
    public FieldDto getField(UUID farmId, UUID fieldId, UUID userId) {
        authService.requireMembership(farmId, userId);
        FieldEntity field = fieldRepository.findByIdAndFarmId(fieldId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found: " + fieldId));
        return FieldDto.fromEntity(field);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<FieldDto> listFields(UUID farmId, UUID userId, Pageable pageable) {
        authService.requireMembership(farmId, userId);
        Page<FieldEntity> page = fieldRepository.findByFarmId(farmId, pageable);
        return PageEnvelope.from(page.map(FieldDto::fromEntity));
    }

    @Transactional
    public FieldDto updateField(UUID farmId, UUID fieldId, UpdateFieldRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_OPERATE_AGRONOMY);
        FieldEntity field = fieldRepository.findByIdAndFarmId(fieldId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found: " + fieldId));

        if (req.name() != null && !req.name().isBlank()) {
            field.setName(req.name());
        }
        if (req.area() != null) {
            field.setArea(new BigDecimal(req.area()));
        }
        if (req.areaUnit() != null && !req.areaUnit().isBlank()) {
            field.setAreaUnit(req.areaUnit());
        }
        if (req.boundaryGeojson() != null) {
            field.setBoundaryGeojson(req.boundaryGeojson());
        }
        if (req.notes() != null) {
            field.setNotes(req.notes());
        }

        field = fieldRepository.save(field);
        return FieldDto.fromEntity(field);
    }
}
