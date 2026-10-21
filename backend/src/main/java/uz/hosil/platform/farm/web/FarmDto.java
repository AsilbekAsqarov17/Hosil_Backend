package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.FarmEntity;

import java.time.Instant;
import java.util.UUID;

public record FarmDto(
        UUID id,
        UUID ownerUserId,
        String name,
        String region,
        String district,
        String totalAreaHa,
        String legalType,
        Instant createdAt
) {
    public static FarmDto fromEntity(FarmEntity entity) {
        return new FarmDto(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getRegion(),
                entity.getDistrict(),
                entity.getTotalAreaHa() != null ? entity.getTotalAreaHa().toPlainString() : null,
                entity.getLegalType(),
                entity.getCreatedAt()
        );
    }
}
