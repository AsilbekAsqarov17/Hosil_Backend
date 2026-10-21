package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.HarvestBatchEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public DTO representing a physical harvest batch.
 */
public record HarvestBatchDto(
        UUID id,
        UUID farmId,
        UUID cropCycleId,
        String cropType,
        String qualityGrade,
        BigDecimal recordedQuantity,
        BigDecimal unallocatedQuantity,
        String unitCode,
        LocalDate harvestDate,
        String storageLocationNotes,
        String status,
        Instant createdAt
) {
    public static HarvestBatchDto fromEntity(HarvestBatchEntity entity) {
        return new HarvestBatchDto(
                entity.getId(),
                entity.getFarmId(),
                entity.getCropCycleId(),
                entity.getCropType(),
                entity.getQualityGrade(),
                entity.getRecordedQuantity(),
                entity.getUnallocatedQuantity(),
                entity.getUnitCode(),
                entity.getHarvestDate(),
                entity.getStorageLocationNotes(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
