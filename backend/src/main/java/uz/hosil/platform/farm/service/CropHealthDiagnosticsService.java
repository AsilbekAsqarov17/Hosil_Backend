package uz.hosil.platform.farm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.farm.domain.CropDiseaseDiagnosisEntity;
import uz.hosil.platform.farm.domain.FarmActivityEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.CropDiseaseDiagnosisRepository;
import uz.hosil.platform.farm.repository.FarmActivityRepository;
import uz.hosil.platform.farm.repository.FieldRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration service for AI crop-disease diagnosis submissions, image verification,
 * model inference dispatch, and agronomic activity conversions.
 * Conforms to docs/01_prd.md AI-001..AI-007 and docs/02_architecture.md §8.4.
 */
@Service
public class CropHealthDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(CropHealthDiagnosticsService.class);

    private final CropDiseaseDiagnosisRepository diagnosisRepository;
    private final FieldRepository fieldRepository;
    private final CropCycleRepository cropCycleRepository;
    private final FarmActivityRepository farmActivityRepository;
    private final FarmAuthorizationService farmAuthorizationService;
    private final CropImageValidator imageValidator;
    private final CropImageStorageService imageStorageService;
    private final CropDiseaseInferencePort inferenceGateway;

    public CropHealthDiagnosticsService(
            CropDiseaseDiagnosisRepository diagnosisRepository,
            FieldRepository fieldRepository,
            CropCycleRepository cropCycleRepository,
            FarmActivityRepository farmActivityRepository,
            FarmAuthorizationService farmAuthorizationService,
            CropImageValidator imageValidator,
            CropImageStorageService imageStorageService,
            CropDiseaseInferencePort inferenceGateway) {
        this.diagnosisRepository = diagnosisRepository;
        this.fieldRepository = fieldRepository;
        this.cropCycleRepository = cropCycleRepository;
        this.farmActivityRepository = farmActivityRepository;
        this.farmAuthorizationService = farmAuthorizationService;
        this.imageValidator = imageValidator;
        this.imageStorageService = imageStorageService;
        this.inferenceGateway = inferenceGateway;
    }

    /**
     * Ingests, validates, and stores a diagnostic crop leaf image, then executes inference.
     */
    @Transactional
    public CropDiseaseDiagnosisEntity submitDiagnosis(
            UUID requestingUserId,
            MultipartFile file,
            String cropType,
            UUID farmId,
            UUID fieldId,
            UUID cropCycleId,
            String userNotes,
            String idempotencyKey) {

        if (cropType == null || cropType.isBlank()) {
            throw new ValidationException("Ekin turi (cropType) koʻrsatilishi shart");
        }

        // Idempotency check: prevent duplicate submissions on client retry
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CropDiseaseDiagnosisEntity> existing = diagnosisRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                CropDiseaseDiagnosisEntity entity = existing.get();
                if (!entity.getUserId().equals(requestingUserId)) {
                    throw new AccessDeniedException("Idempotency-Key boshqa foydalanuvchiga tegishli");
                }
                log.info("Idempotent diagnosis hit for key: {}, returning existing diagnosis: {}", idempotencyKey, entity.getId());
                return entity;
            }
        }

        // Validate farm and field access if provided
        if (farmId != null) {
            farmAuthorizationService.requireMembership(farmId, requestingUserId);

            if (fieldId != null) {
                if (!fieldRepository.existsByIdAndFarmId(fieldId, farmId)) {
                    throw new ResourceNotFoundException("Koʻrsatilgan maydon fermer xoʻjaligida topilmadi: " + fieldId);
                }
            }

            if (cropCycleId != null) {
                if (cropCycleRepository.findByIdAndFarmId(cropCycleId, farmId).isEmpty()) {
                    throw new ResourceNotFoundException("Koʻrsatilgan ekin sikli fermer xoʻjaligida topilmadi: " + cropCycleId);
                }
            }
        } else if (fieldId != null) {
            // Infer farmId from field if fieldId given without farmId
            FieldEntity field = fieldRepository.findById(fieldId)
                    .orElseThrow(() -> new ResourceNotFoundException("Maydon topilmadi: " + fieldId));
            farmId = field.getFarmId();
            farmAuthorizationService.requireMembership(farmId, requestingUserId);
        }

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Rasm fayli yuklanishi shart");
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Rasm baytlarini oʻqishda xatolik yuz berdi: " + e.getMessage(), e);
        }

        // Validate size, magic bytes, dimensions, and decodability
        CropImageValidator.ValidatedImage validatedImage = imageValidator.validate(rawBytes);

        UUID diagnosisId = UUID.randomUUID();

        // Strip EXIF / GPS metadata and save pure sanitized JPEG into private storage
        CropImageStorageService.StoredImageResult stored = imageStorageService.storeSanitizedImage(
                diagnosisId,
                validatedImage.bufferedImage()
        );

        // Create persistent record
        CropDiseaseDiagnosisEntity diagnosis = new CropDiseaseDiagnosisEntity(
                diagnosisId,
                requestingUserId,
                farmId,
                fieldId,
                cropCycleId,
                cropType.trim(),
                userNotes,
                stored.storagePath(),
                stored.contentType(),
                stored.fileSize(),
                validatedImage.width(),
                validatedImage.height(),
                idempotencyKey
        );
        diagnosis.setStatus("PROCESSING");
        diagnosis = diagnosisRepository.saveAndFlush(diagnosis);

        // Execute inference via SPI
        InferenceRequest inferenceReq = new InferenceRequest(
                diagnosisId,
                cropType.trim(),
                stored.sanitizedBytes(),
                userNotes
        );

        InferenceResult result;
        try {
            result = inferenceGateway.inferCropDisease(inferenceReq);
        } catch (Exception e) {
            log.error("AI inference execution threw an unhandled exception for diagnosisId: {}", diagnosisId, e);
            result = InferenceResult.failure("AI tahlil xizmatida kutilmagan nosozlik: " + e.getMessage(), "Hosil-CropVision", "unknown");
        }

        applyInferenceResult(diagnosis, result);
        return diagnosisRepository.save(diagnosis);
    }

    /**
     * Retrieves diagnosis detail after strictly validating user or farm membership permissions.
     */
    @Transactional(readOnly = true)
    public CropDiseaseDiagnosisEntity getDiagnosis(UUID diagnosisId, UUID requestingUserId) {
        CropDiseaseDiagnosisEntity diagnosis = diagnosisRepository.findById(diagnosisId)
                .orElseThrow(() -> new ResourceNotFoundException("Tashxis yozuvi topilmadi: " + diagnosisId));

        validateAccess(diagnosis, requestingUserId);
        return diagnosis;
    }

    /**
     * Returns paginated diagnostic scan history for a user, optionally filtered by farm.
     */
    @Transactional(readOnly = true)
    public Page<CropDiseaseDiagnosisEntity> listUserDiagnoses(UUID requestingUserId, UUID farmId, Pageable pageable) {
        if (farmId != null) {
            farmAuthorizationService.requireMembership(farmId, requestingUserId);
            return diagnosisRepository.findByFarmIdOrderByCreatedAtDesc(farmId, pageable);
        }
        return diagnosisRepository.findByUserIdOrderByCreatedAtDesc(requestingUserId, pageable);
    }

    /**
     * Retries diagnosis execution for a failed or queued diagnosis without requiring image re-upload.
     */
    @Transactional
    public CropDiseaseDiagnosisEntity retryDiagnosis(UUID diagnosisId, UUID requestingUserId) {
        CropDiseaseDiagnosisEntity diagnosis = diagnosisRepository.findByIdForUpdate(diagnosisId)
                .orElseThrow(() -> new ResourceNotFoundException("Tashxis yozuvi topilmadi: " + diagnosisId));

        validateAccess(diagnosis, requestingUserId);

        if ("COMPLETED".equals(diagnosis.getStatus())) {
            log.info("Diagnosis {} already COMPLETED. Retrying is an idempotent no-op.", diagnosisId);
            return diagnosis;
        }

        byte[] imageBytes = imageStorageService.readImageBytes(diagnosis.getImageStoragePath());

        diagnosis.setStatus("PROCESSING");
        diagnosis.setFailureReason(null);
        diagnosis = diagnosisRepository.saveAndFlush(diagnosis);

        InferenceRequest inferenceReq = new InferenceRequest(
                diagnosisId,
                diagnosis.getCropType(),
                imageBytes,
                diagnosis.getUserNotes()
        );

        InferenceResult result;
        try {
            result = inferenceGateway.inferCropDisease(inferenceReq);
        } catch (Exception e) {
            log.error("AI inference retry failed for diagnosisId: {}", diagnosisId, e);
            result = InferenceResult.failure("Qayta tahlilda xatolik yuz berdi: " + e.getMessage(), "Hosil-CropVision", "unknown");
        }

        applyInferenceResult(diagnosis, result);
        return diagnosisRepository.save(diagnosis);
    }

    /**
     * Converts a completed diagnosis into a field activity (scouting observation or treatment record).
     */
    @Transactional
    public FarmActivityEntity convertToActivity(
            UUID diagnosisId,
            UUID requestingUserId,
            String activityType,
            String additionalNotes) {

        CropDiseaseDiagnosisEntity diagnosis = getDiagnosis(diagnosisId, requestingUserId);

        if (diagnosis.getFarmId() == null || diagnosis.getFieldId() == null) {
            throw new ValidationException("Tashxisni fermerlik faoliyatiga biriktirish uchun xoʻjalik (farmId) va maydon (fieldId) mavjud boʻlishi shart");
        }

        farmAuthorizationService.requireActivityWritePermission(diagnosis.getFarmId(), requestingUserId);

        String effectiveActivityType = (activityType != null && !activityType.isBlank())
                ? activityType.trim().toUpperCase()
                : (Boolean.TRUE.equals(diagnosis.getIsHealthy()) ? "SCOUTING" : "PESTICIDE");

        StringBuilder notesBuilder = new StringBuilder();
        notesBuilder.append("[AI Kasallik Tahlili Natijasi]\n");
        notesBuilder.append("Holat: ").append(diagnosis.getConditionName() != null ? diagnosis.getConditionName() : "Nomaʼlum").append("\n");
        if (diagnosis.getConfidenceScore() != null) {
            notesBuilder.append("Ishonchlilik darajasi: ").append(diagnosis.getConfidenceScore().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)).append("%\n");
        }
        if (diagnosis.getCulturalControls() != null && !diagnosis.getCulturalControls().isBlank()) {
            notesBuilder.append("Tavsiya etilgan choralar: ").append(diagnosis.getCulturalControls()).append("\n");
        }
        if (additionalNotes != null && !additionalNotes.isBlank()) {
            notesBuilder.append("Fermer eslatmasi: ").append(additionalNotes.trim());
        }

        FarmActivityEntity activity = new FarmActivityEntity(
                UUID.randomUUID(),
                diagnosis.getFarmId(),
                diagnosis.getCropCycleId(),
                diagnosis.getFieldId(),
                requestingUserId,
                effectiveActivityType,
                Instant.now(),
                null,
                null,
                notesBuilder.toString(),
                1L,
                UUID.randomUUID().toString()
        );

        return farmActivityRepository.save(activity);
    }

    /**
     * Retrieves the sanitized image binary from private storage after verifying authorization.
     */
    @Transactional(readOnly = true)
    public byte[] getImageBytes(UUID diagnosisId, UUID requestingUserId) {
        CropDiseaseDiagnosisEntity diagnosis = diagnosisRepository.findById(diagnosisId)
                .orElseThrow(() -> new ResourceNotFoundException("Tashxis yozuvi topilmadi: " + diagnosisId));

        validateAccess(diagnosis, requestingUserId);
        return imageStorageService.readImageBytes(diagnosis.getImageStoragePath());
    }

    private void validateAccess(CropDiseaseDiagnosisEntity diagnosis, UUID requestingUserId) {
        if (diagnosis.getUserId().equals(requestingUserId)) {
            return;
        }
        if (diagnosis.getFarmId() != null) {
            try {
                farmAuthorizationService.requireMembership(diagnosis.getFarmId(), requestingUserId);
                return;
            } catch (Exception ignored) {}
        }
        throw new AccessDeniedException("Siz ushbu tashxis maʼlumotlarini koʻrish huquqiga ega emassiz");
    }

    private void applyInferenceResult(CropDiseaseDiagnosisEntity diagnosis, InferenceResult result) {
        diagnosis.setStatus(result.status());
        diagnosis.setConditionName(result.conditionName());
        diagnosis.setConditionCode(result.conditionCode());
        diagnosis.setConfidenceScore(result.confidenceScore());
        diagnosis.setIsHealthy(result.isHealthy());
        diagnosis.setSeverity(result.severity());
        diagnosis.setAgronomicAdvice(result.agronomicAdvice());
        diagnosis.setCulturalControls(result.culturalControls());
        diagnosis.setRecommendedChecks(result.recommendedChecks());
        diagnosis.setModelName(result.modelName());
        diagnosis.setModelVersion(result.modelVersion());
        diagnosis.setIsSimulated(result.isSimulated());
        diagnosis.setFailureReason(result.failureReason());
    }
}
