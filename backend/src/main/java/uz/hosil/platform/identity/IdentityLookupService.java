package uz.hosil.platform.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.identity.domain.CarrierProfileEntity;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.repository.CarrierProfileRepository;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public module boundary service for identity, profile, seller, and carrier verification lookups across modules.
 */
@Service
public class IdentityLookupService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final CarrierProfileRepository carrierProfileRepository;

    public IdentityLookupService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            SellerProfileRepository sellerProfileRepository,
            CarrierProfileRepository carrierProfileRepository
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.carrierProfileRepository = carrierProfileRepository;
    }

    public Optional<UserPublicSummary> findUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(user -> {
                    String fullName = userProfileRepository.findByUserId(user.getId())
                            .map(profile -> profile.getFullName())
                            .orElse(null);
                    return new UserPublicSummary(user.getId(), user.getPhoneNumber(), fullName);
                });
    }

    public Optional<UserPublicSummary> findUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String fullName = userProfileRepository.findByUserId(user.getId())
                            .map(profile -> profile.getFullName())
                            .orElse(null);
                    return new UserPublicSummary(user.getId(), user.getPhoneNumber(), fullName);
                });
    }

    public Optional<SellerProfileInfo> findSellerProfile(UUID userId) {
        return sellerProfileRepository.findByUserId(userId)
                .map(profile -> new SellerProfileInfo(
                        profile.getId(),
                        profile.getUserId(),
                        profile.getSellerType(),
                        profile.getBusinessName(),
                        profile.getInn(),
                        profile.getVerificationStatus(),
                        profile.getVerifiedAt()
                ));
    }

    public boolean isVerifiedCommercialSupplier(UUID userId) {
        return sellerProfileRepository.findByUserId(userId)
                .map(profile -> "VERIFIED".equalsIgnoreCase(profile.getVerificationStatus())
                        && ("COMMERCIAL_SUPPLIER".equalsIgnoreCase(profile.getSellerType())
                        || "COOPERATIVE".equalsIgnoreCase(profile.getSellerType())))
                .orElse(false);
    }

    public Optional<CarrierProfileInfo> findCarrierProfile(UUID userId) {
        return carrierProfileRepository.findByUserId(userId)
                .map(profile -> new CarrierProfileInfo(
                        profile.getId(),
                        profile.getUserId(),
                        profile.getCompanyName(),
                        profile.getLicenseNumber(),
                        profile.getVerificationStatus(),
                        profile.getVerifiedAt()
                ));
    }

    public boolean isVerifiedCarrier(UUID userId) {
        return carrierProfileRepository.findByUserId(userId)
                .map(profile -> "VERIFIED".equalsIgnoreCase(profile.getVerificationStatus()))
                .orElse(false);
    }

    @Transactional
    public CarrierProfileInfo registerOrUpdateCarrierProfile(
            UUID userId,
            String companyName,
            String licenseNumber
    ) {
        CarrierProfileEntity profile = carrierProfileRepository.findByUserId(userId)
                .orElseGet(() -> new CarrierProfileEntity(
                        UUID.randomUUID(),
                        userId,
                        companyName,
                        licenseNumber,
                        "PENDING",
                        null
                ));

        if (companyName != null && !companyName.isBlank()) {
            profile.setCompanyName(companyName.trim());
        }
        if (licenseNumber != null && !licenseNumber.isBlank()) {
            profile.setLicenseNumber(licenseNumber.trim());
        }

        // Self-service cannot self-grant VERIFIED status
        if ("UNVERIFIED".equalsIgnoreCase(profile.getVerificationStatus())
                || "REJECTED".equalsIgnoreCase(profile.getVerificationStatus())) {
            profile.setVerificationStatus("PENDING");
            profile.setVerifiedAt(null);
        }

        profile = carrierProfileRepository.save(profile);

        return new CarrierProfileInfo(
                profile.getId(),
                profile.getUserId(),
                profile.getCompanyName(),
                profile.getLicenseNumber(),
                profile.getVerificationStatus(),
                profile.getVerifiedAt()
        );
    }

    @Transactional
    public CarrierProfileInfo adminSetCarrierVerificationStatus(UUID userId, String newStatus) {
        String normalizedStatus = newStatus != null ? newStatus.trim().toUpperCase() : "PENDING";
        if (!List.of("UNVERIFIED", "PENDING", "VERIFIED", "REJECTED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid carrier verification status: " + newStatus);
        }

        CarrierProfileEntity profile = carrierProfileRepository.findByUserId(userId)
                .orElseGet(() -> new CarrierProfileEntity(
                        UUID.randomUUID(),
                        userId,
                        "Carrier " + userId,
                        "LIC-TEMP",
                        normalizedStatus,
                        "VERIFIED".equals(normalizedStatus) ? Instant.now() : null
                ));

        profile.setVerificationStatus(normalizedStatus);
        if ("VERIFIED".equals(normalizedStatus)) {
            profile.setVerifiedAt(Instant.now());
        } else {
            profile.setVerifiedAt(null);
        }

        profile = carrierProfileRepository.save(profile);

        return new CarrierProfileInfo(
                profile.getId(),
                profile.getUserId(),
                profile.getCompanyName(),
                profile.getLicenseNumber(),
                profile.getVerificationStatus(),
                profile.getVerifiedAt()
        );
    }

    @Transactional
    public CarrierProfileInfo upsertCarrierProfile(
            UUID userId,
            String companyName,
            String licenseNumber,
            String verificationStatus
    ) {
        CarrierProfileEntity profile = carrierProfileRepository.findByUserId(userId)
                .orElseGet(() -> new CarrierProfileEntity(
                        UUID.randomUUID(),
                        userId,
                        companyName,
                        licenseNumber,
                        verificationStatus != null ? verificationStatus : "PENDING",
                        "VERIFIED".equalsIgnoreCase(verificationStatus) ? Instant.now() : null
                ));

        if (companyName != null) {
            profile.setCompanyName(companyName);
        }
        if (licenseNumber != null) {
            profile.setLicenseNumber(licenseNumber);
        }
        if (verificationStatus != null) {
            profile.setVerificationStatus(verificationStatus);
            if ("VERIFIED".equalsIgnoreCase(verificationStatus) && profile.getVerifiedAt() == null) {
                profile.setVerifiedAt(Instant.now());
            }
        }
        profile = carrierProfileRepository.save(profile);

        return new CarrierProfileInfo(
                profile.getId(),
                profile.getUserId(),
                profile.getCompanyName(),
                profile.getLicenseNumber(),
                profile.getVerificationStatus(),
                profile.getVerifiedAt()
        );
    }
}

