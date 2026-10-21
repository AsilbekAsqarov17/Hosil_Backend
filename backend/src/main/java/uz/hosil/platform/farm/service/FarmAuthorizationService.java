package uz.hosil.platform.farm.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Enforces ABAC and RBAC security boundaries across farm tenant resources.
 */
@Service
public class FarmAuthorizationService {

    public static final Set<String> CAN_MANAGE_FARM = Set.of("OWNER", "MANAGER");
    public static final Set<String> CAN_MANAGE_MEMBERS = Set.of("OWNER");
    public static final Set<String> CAN_OPERATE_AGRONOMY = Set.of("OWNER", "MANAGER", "AGRONOMIST");
    public static final Set<String> ALL_MEMBERS = Set.of("OWNER", "MANAGER", "AGRONOMIST", "WORKER");

    private final FarmRepository farmRepository;
    private final FarmMembershipRepository membershipRepository;

    public FarmAuthorizationService(FarmRepository farmRepository, FarmMembershipRepository membershipRepository) {
        this.farmRepository = farmRepository;
        this.membershipRepository = membershipRepository;
    }

    public FarmMembershipEntity requireMembership(UUID farmId, UUID userId) {
        if (!farmRepository.existsById(farmId)) {
            throw new ResourceNotFoundException("Farm not found: " + farmId);
        }
        return membershipRepository.findByFarmIdAndUserIdAndStatus(farmId, userId, "ACTIVE")
                .orElseThrow(() -> new AccessDeniedException("User does not have active membership in farm: " + farmId));
    }

    public FarmMembershipEntity requireRole(UUID farmId, UUID userId, Set<String> allowedRoles) {
        FarmMembershipEntity membership = requireMembership(farmId, userId);
        if (!allowedRoles.contains(membership.getRole())) {
            throw new AccessDeniedException("User role '" + membership.getRole() + "' is insufficient for this operation");
        }
        return membership;
    }

    public FarmMembershipEntity requireActivityWritePermission(UUID farmId, UUID userId) {
        FarmMembershipEntity membership = requireMembership(farmId, userId);
        if (CAN_OPERATE_AGRONOMY.contains(membership.getRole())) {
            return membership;
        }
        if (membership.getPermissionsMask() != null && membership.getPermissionsMask().contains("WRITE_ACTIVITY")) {
            return membership;
        }
        throw new AccessDeniedException("User does not have WRITE_ACTIVITY permission on farm: " + farmId);
    }

    public FarmMembershipEntity requireHarvestRecordPermission(UUID farmId, UUID userId) {
        FarmMembershipEntity membership = requireMembership(farmId, userId);
        if (CAN_OPERATE_AGRONOMY.contains(membership.getRole())) {
            return membership;
        }
        if (membership.getPermissionsMask() != null && membership.getPermissionsMask().contains("RECORD_HARVEST")) {
            return membership;
        }
        throw new AccessDeniedException("User does not have RECORD_HARVEST permission on farm: " + farmId);
    }

    public FarmMembershipEntity requireMarketPublishPermission(UUID farmId, UUID userId) {
        FarmMembershipEntity membership = requireMembership(farmId, userId);
        if ("OWNER".equalsIgnoreCase(membership.getRole())) {
            return membership;
        }
        if (membership.getPermissionsMask() != null && membership.getPermissionsMask().contains("PUBLISH_MARKET")) {
            return membership;
        }
        throw new AccessDeniedException("User does not have PUBLISH_MARKET permission on farm: " + farmId);
    }
}

