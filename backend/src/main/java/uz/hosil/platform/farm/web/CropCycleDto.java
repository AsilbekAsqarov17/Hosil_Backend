package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.CropCycleEntity;

import java.time.LocalDate;
import java.util.UUID;

public record CropCycleDto(
        UUID id,
        UUID fieldId,
        String cropType,
        String variety,
        LocalDate plantingDate,
        LocalDate expectedHarvestDate,
        LocalDate actualHarvestDate,
        String status
) {
    public static CropCycleDto fromEntity(CropCycleEntity entity) {
        return new CropCycleDto(
                entity.getId(),
                entity.getFieldId(),
                entity.getCropType(),
                entity.getVariety(),
                entity.getPlantingDate(),
                entity.getExpectedHarvestDate(),
                entity.getActualHarvestDate(),
                entity.getStatus()
        );
    }
}
