package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.FieldEntity;

import java.util.UUID;

public record FieldDto(
        UUID id,
        UUID farmId,
        String name,
        String area,
        String areaUnit,
        String boundaryGeojson,
        String notes
) {
    public static FieldDto fromEntity(FieldEntity entity) {
        return new FieldDto(
                entity.getId(),
                entity.getFarmId(),
                entity.getName(),
                entity.getArea() != null ? entity.getArea().toPlainString() : null,
                entity.getAreaUnit(),
                entity.getBoundaryGeojson(),
                entity.getNotes()
        );
    }
}
