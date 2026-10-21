package uz.hosil.platform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Public summary record of carrier profile and verification details across module boundaries (DEC-011, LOG-001).
 */
public record CarrierProfileInfo(
        UUID id,
        UUID userId,
        String companyName,
        String licenseNumber,
        String verificationStatus,
        Instant verifiedAt
) {}
