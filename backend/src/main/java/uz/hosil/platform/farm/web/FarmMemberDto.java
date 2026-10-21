package uz.hosil.platform.farm.web;

import uz.hosil.platform.farm.domain.FarmMembershipEntity;

import java.util.UUID;

public record FarmMemberDto(
        UUID id,
        UUID farmId,
        UUID userId,
        String fullName,
        String phoneNumber,
        String role,
        String permissionsMask,
        String status
) {
    public static FarmMemberDto fromEntity(FarmMembershipEntity entity, String fullName, String phoneNumber) {
        return new FarmMemberDto(
                entity.getId(),
                entity.getFarmId(),
                entity.getUserId(),
                fullName,
                phoneNumber,
                entity.getRole(),
                entity.getPermissionsMask(),
                entity.getStatus()
        );
    }
}
