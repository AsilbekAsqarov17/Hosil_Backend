package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.CropDiseaseDiagnosisEntity;
import uz.hosil.platform.farm.service.MockSandboxCropDiseaseInferenceGateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response DTO for crop-health diagnosis records.
 */
public record CropDiseaseDiagnosisDto(
        UUID id,
        UUID userId,
        UUID farmId,
        UUID fieldId,
        UUID cropCycleId,
        String cropType,
        String userNotes,
        String imageContentType,
        Long imageFileSize,
        Integer imageWidth,
        Integer imageHeight,
        String status,
        String conditionName,
        String conditionCode,
        BigDecimal confidenceScore,
        Boolean isHealthy,
        String severity,
        String agronomicAdvice,
        String culturalControls,
        String recommendedChecks,
        String modelName,
        String modelVersion,
        Boolean isSimulated,
        String disclaimer,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static CropDiseaseDiagnosisDto fromEntity(CropDiseaseDiagnosisEntity entity) {
        return new CropDiseaseDiagnosisDto(
                entity.getId(),
                entity.getUserId(),
                entity.getFarmId(),
                entity.getFieldId(),
                entity.getCropCycleId(),
                entity.getCropType(),
                entity.getUserNotes(),
                entity.getImageContentType(),
                entity.getImageFileSize(),
                entity.getImageWidth(),
                entity.getImageHeight(),
                entity.getStatus(),
                entity.getConditionName(),
                entity.getConditionCode(),
                entity.getConfidenceScore(),
                entity.getIsHealthy(),
                entity.getSeverity(),
                entity.getAgronomicAdvice(),
                entity.getCulturalControls(),
                entity.getRecommendedChecks(),
                entity.getModelName(),
                entity.getModelVersion(),
                entity.getIsSimulated(),
                MockSandboxCropDiseaseInferenceGateway.STANDARD_AGRONOMIC_DISCLAIMER,
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
