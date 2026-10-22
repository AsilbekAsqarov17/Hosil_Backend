package uz.hosil.platform.farm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an AI crop-disease diagnosis submission and results (docs/01_prd.md AI-001..AI-007).
 */
@Entity
@Table(name = "crop_disease_diagnoses")
public class CropDiseaseDiagnosisEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "field_id")
    private UUID fieldId;

    @Column(name = "crop_cycle_id")
    private UUID cropCycleId;

    @Column(name = "crop_type", nullable = false, length = 64)
    private String cropType;

    @Column(name = "user_notes", columnDefinition = "TEXT")
    private String userNotes;

    @Column(name = "image_storage_path", nullable = false, length = 512)
    private String imageStoragePath;

    @Column(name = "image_content_type", nullable = false, length = 64)
    private String imageContentType;

    @Column(name = "image_file_size", nullable = false)
    private Long imageFileSize;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "QUEUED";

    @Column(name = "condition_name", length = 128)
    private String conditionName;

    @Column(name = "condition_code", length = 64)
    private String conditionCode;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "is_healthy")
    private Boolean isHealthy;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "agronomic_advice", columnDefinition = "TEXT")
    private String agronomicAdvice;

    @Column(name = "cultural_controls", columnDefinition = "TEXT")
    private String culturalControls;

    @Column(name = "recommended_checks", columnDefinition = "TEXT")
    private String recommendedChecks;

    @Column(name = "model_name", length = 64)
    private String modelName;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated = false;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public CropDiseaseDiagnosisEntity() {}

    public CropDiseaseDiagnosisEntity(UUID id, UUID userId, UUID farmId, UUID fieldId, UUID cropCycleId,
                                      String cropType, String userNotes, String imageStoragePath,
                                      String imageContentType, Long imageFileSize, Integer imageWidth,
                                      Integer imageHeight, String idempotencyKey) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.farmId = farmId;
        this.fieldId = fieldId;
        this.cropCycleId = cropCycleId;
        this.cropType = cropType;
        this.userNotes = userNotes;
        this.imageStoragePath = imageStoragePath;
        this.imageContentType = imageContentType;
        this.imageFileSize = imageFileSize;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.idempotencyKey = idempotencyKey;
        this.status = "QUEUED";
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (isSimulated == null) {
            isSimulated = false;
        }
        if (status == null) {
            status = "QUEUED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getFieldId() { return fieldId; }
    public void setFieldId(UUID fieldId) { this.fieldId = fieldId; }

    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getUserNotes() { return userNotes; }
    public void setUserNotes(String userNotes) { this.userNotes = userNotes; }

    public String getImageStoragePath() { return imageStoragePath; }
    public void setImageStoragePath(String imageStoragePath) { this.imageStoragePath = imageStoragePath; }

    public String getImageContentType() { return imageContentType; }
    public void setImageContentType(String imageContentType) { this.imageContentType = imageContentType; }

    public Long getImageFileSize() { return imageFileSize; }
    public void setImageFileSize(Long imageFileSize) { this.imageFileSize = imageFileSize; }

    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }

    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConditionName() { return conditionName; }
    public void setConditionName(String conditionName) { this.conditionName = conditionName; }

    public String getConditionCode() { return conditionCode; }
    public void setConditionCode(String conditionCode) { this.conditionCode = conditionCode; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public Boolean getIsHealthy() { return isHealthy; }
    public void setIsHealthy(Boolean isHealthy) { this.isHealthy = isHealthy; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getAgronomicAdvice() { return agronomicAdvice; }
    public void setAgronomicAdvice(String agronomicAdvice) { this.agronomicAdvice = agronomicAdvice; }

    public String getCulturalControls() { return culturalControls; }
    public void setCulturalControls(String culturalControls) { this.culturalControls = culturalControls; }

    public String getRecommendedChecks() { return recommendedChecks; }
    public void setRecommendedChecks(String recommendedChecks) { this.recommendedChecks = recommendedChecks; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public Boolean getIsSimulated() { return isSimulated; }
    public void setIsSimulated(Boolean isSimulated) { this.isSimulated = isSimulated; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
