package uz.hosil.platform.farm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.web.AddFarmMemberRequest;
import uz.hosil.platform.farm.web.CreateFarmRequest;
import uz.hosil.platform.farm.web.FarmDto;
import uz.hosil.platform.farm.web.FarmMemberDto;
import uz.hosil.platform.farm.web.PageEnvelope;
import uz.hosil.platform.farm.web.UpdateFarmRequest;
import uz.hosil.platform.identity.IdentityLookupService;
import uz.hosil.platform.identity.UserPublicSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing farm profile CRUD and membership administration.
 */
@Service
public class FarmService {

    private final FarmRepository farmRepository;
    private final FarmMembershipRepository membershipRepository;
    private final FarmAuthorizationService authService;
    private final IdentityLookupService identityLookupService;

    public FarmService(FarmRepository farmRepository,
                       FarmMembershipRepository membershipRepository,
                       FarmAuthorizationService authService,
                       IdentityLookupService identityLookupService) {
        this.farmRepository = farmRepository;
        this.membershipRepository = membershipRepository;
        this.authService = authService;
        this.identityLookupService = identityLookupService;
    }

    @Transactional
    public FarmDto createFarm(CreateFarmRequest req, UUID userId) {
        BigDecimal totalArea = req.totalAreaHa() != null ? new BigDecimal(req.totalAreaHa()) : null;
        FarmEntity farm = new FarmEntity(
                userId,
                req.name(),
                req.region(),
                req.district(),
                totalArea,
                req.legalType()
        );
        farm = farmRepository.save(farm);

        FarmMembershipEntity membership = new FarmMembershipEntity(
                farm.getId(),
                userId,
                "OWNER",
                "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET",
                "ACTIVE"
        );
        membershipRepository.save(membership);

        return FarmDto.fromEntity(farm);
    }

    @Transactional(readOnly = true)
    public FarmDto getFarmById(UUID farmId, UUID userId) {
        authService.requireMembership(farmId, userId);
        FarmEntity farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm not found: " + farmId));
        return FarmDto.fromEntity(farm);
    }

    @Transactional
    public FarmDto updateFarm(UUID farmId, UpdateFarmRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_MANAGE_FARM);
        FarmEntity farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm not found: " + farmId));

        if (req.name() != null && !req.name().isBlank()) {
            farm.setName(req.name());
        }
        if (req.totalAreaHa() != null) {
            farm.setTotalAreaHa(new BigDecimal(req.totalAreaHa()));
        }
        if (req.legalType() != null && !req.legalType().isBlank()) {
            farm.setLegalType(req.legalType());
        }

        farm = farmRepository.save(farm);
        return FarmDto.fromEntity(farm);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<FarmDto> listAccessibleFarms(UUID userId, Pageable pageable) {
        Page<FarmEntity> page = farmRepository.findAccessibleFarms(userId, pageable);
        return PageEnvelope.from(page.map(FarmDto::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<FarmMemberDto> listMembers(UUID farmId, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_MANAGE_FARM);
        List<FarmMembershipEntity> memberships = membershipRepository.findByFarmId(farmId);
        List<FarmMemberDto> dtos = new ArrayList<>();

        for (FarmMembershipEntity m : memberships) {
            Optional<UserPublicSummary> userOpt = identityLookupService.findUserById(m.getUserId());
            String fullName = userOpt.map(UserPublicSummary::fullName).orElse(null);
            String phoneNumber = userOpt.map(UserPublicSummary::phoneNumber).orElse(null);

            dtos.add(FarmMemberDto.fromEntity(m, fullName, phoneNumber));
        }

        return dtos;
    }

    @Transactional
    public FarmMemberDto addMember(UUID farmId, AddFarmMemberRequest req, UUID userId) {
        authService.requireRole(farmId, userId, FarmAuthorizationService.CAN_MANAGE_MEMBERS);

        UserPublicSummary targetUser = identityLookupService.findUserByPhoneNumber(req.phoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi: " + req.phoneNumber()));

        Optional<FarmMembershipEntity> existing = membershipRepository.findByFarmIdAndUserId(farmId, targetUser.userId());
        FarmMembershipEntity membership;

        String mask = req.permissionsMask() != null && !req.permissionsMask().isBlank()
                ? req.permissionsMask()
                : defaultMaskForRole(req.role());

        if (existing.isPresent()) {
            membership = existing.get();
            if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                throw new BusinessConflictException("Foydalanuvchi allaqachon fermer xo'jaligi a'zosi");
            }
            membership.setRole(req.role());
            membership.setPermissionsMask(mask);
            membership.setStatus("ACTIVE");
        } else {
            membership = new FarmMembershipEntity(
                    farmId,
                    targetUser.userId(),
                    req.role(),
                    mask,
                    "ACTIVE"
            );
        }

        membership = membershipRepository.save(membership);

        return FarmMemberDto.fromEntity(membership, targetUser.fullName(), targetUser.phoneNumber());
    }

    @Transactional
    public void revokeMembership(UUID farmId, UUID membershipId, UUID callerUserId) {
        authService.requireRole(farmId, callerUserId, FarmAuthorizationService.CAN_MANAGE_MEMBERS);

        FarmMembershipEntity targetMembership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("A'zolik topilmadi: " + membershipId));

        if (!targetMembership.getFarmId().equals(farmId)) {
            throw new ResourceNotFoundException("Ushbu fermer xo'jaligida a'zolik topilmadi: " + membershipId);
        }

        if ("OWNER".equalsIgnoreCase(targetMembership.getRole()) && "ACTIVE".equalsIgnoreCase(targetMembership.getStatus())) {
            long activeOwners = membershipRepository.findByFarmIdAndStatus(farmId, "ACTIVE").stream()
                    .filter(m -> "OWNER".equalsIgnoreCase(m.getRole()))
                    .count();
            if (activeOwners <= 1) {
                throw new BusinessConflictException("Fermer xo'jaligining yagona egasini bekor qilib bo'lmaydi");
            }
        }

        targetMembership.setStatus("REVOKED");
        membershipRepository.save(targetMembership);
    }

    private String defaultMaskForRole(String role) {
        return switch (role) {
            case "OWNER" -> "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET";
            case "MANAGER" -> "READ,WRITE_ACTIVITY,RECORD_HARVEST";
            case "AGRONOMIST" -> "READ,WRITE_ACTIVITY,RECORD_HARVEST";
            case "WORKER" -> "READ,WRITE_ACTIVITY";
            default -> "READ";
        };
    }
}
