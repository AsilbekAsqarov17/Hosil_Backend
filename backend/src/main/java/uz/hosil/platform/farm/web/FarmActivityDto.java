package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.FarmActivityEntity;

import java.time.Instant;
import java.util.UUID;

public record FarmActivityDto(
        UUID id,
        UUID cropCycleId,
        UUID fieldId,
        String activityType,
        Instant activityTimestamp,
        String quantity,
        String unit,
        String notes,
        Long syncVersion
) {
    public static FarmActivityDto fromEntity(FarmActivityEntity entity) {
        return new FarmActivityDto(
                entity.getId(),
                entity.getCropCycleId(),
                entity.getFieldId(),
                entity.getActivityType(),
                entity.getActivityTimestamp(),
                entity.getQuantity() != null ? entity.getQuantity().toPlainString() : null,
                entity.getUnit(),
                entity.getNotes(),
                entity.getSyncVersion()
        );
    }
}
