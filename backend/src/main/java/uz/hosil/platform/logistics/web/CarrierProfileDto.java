package uz.hosil.platform.logistics.web;

import uz.hosil.platform.identity.CarrierProfileInfo;

import java.time.Instant;
import java.util.UUID;

public record CarrierProfileDto(
        UUID id,
        UUID userId,
        String companyName,
        String licenseNumber,
        String verificationStatus,
        Instant verifiedAt
) {
    public static CarrierProfileDto from(CarrierProfileInfo info) {
        return new CarrierProfileDto(
                info.id(),
                info.userId(),
                info.companyName(),
                info.licenseNumber(),
                info.verificationStatus(),
                info.verifiedAt()
        );
    }
}
