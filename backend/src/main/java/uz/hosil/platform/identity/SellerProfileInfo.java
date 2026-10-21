package uz.hosil.platform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Public summary record of seller verification and business details across module boundaries.
 */
public record SellerProfileInfo(
        UUID id,
        UUID userId,
        String sellerType,
        String businessName,
        String inn,
        String verificationStatus,
        Instant verifiedAt
) {}
