package uz.hosil.platform.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.InvalidOtpException;
import uz.hosil.platform.common.error.InvalidRefreshTokenException;
import uz.hosil.platform.common.error.OtpChallengeLockedException;
import uz.hosil.platform.common.error.AccountSuspendedException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.domain.UserRoleEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.identity.repository.UserRoleRepository;
import uz.hosil.platform.identity.telegram.TelegramLinkService;
import uz.hosil.platform.identity.web.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator service for phone OTP authentication, automatic user registration,
 * role assignment, and session token lifecycle.
 * Source of Truth: docs/04_domain_model.md §4.1 and docs/05_api_contract.md §3.1.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final TelegramLinkService telegramLinkService;

    public AuthService(
            OtpService otpService,
            TokenService tokenService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserProfileRepository userProfileRepository,
            TelegramLinkService telegramLinkService
    ) {
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.userProfileRepository = userProfileRepository;
        this.telegramLinkService = telegramLinkService;
    }

    @Transactional
    public TelegramLinkSessionResponse createTelegramLinkSession(TelegramLinkSessionRequest request) {
        TelegramLinkService.TelegramLinkSessionDto session = telegramLinkService.createLinkingSession(request.phoneNumber());
        return new TelegramLinkSessionResponse(
                session.token(),
                session.deepLink(),
                session.botUsername(),
                session.expiresInSeconds()
        );
    }

    @Transactional(readOnly = true)
    public TelegramLinkStatusResponse checkTelegramLinkStatus(TelegramLinkStatusRequest request) {
        if (request == null || request.sessionToken() == null || request.sessionToken().isBlank()) {
            throw new ValidationException("Sessiya tokeni koʻrsatilishi shart");
        }
        return telegramLinkService.checkLinkingStatusBySessionToken(request.sessionToken());
    }

    @Transactional
    public OtpChallengeResponse requestOtp(OtpRequest request) {
        OtpService.OtpChallengeResult result = otpService.requestOtpChallenge(request.phoneNumber());
        return new OtpChallengeResponse(result.challengeId(), result.expiresInSeconds(), result.resendAfterSeconds());
    }

    @Transactional(noRollbackFor = {InvalidOtpException.class, OtpChallengeLockedException.class})
    public AuthTokenResponse verifyOtp(OtpVerifyRequest request, String ipAddress) {
        String verifiedPhone = otpService.verifyOtpChallenge(request.challengeId(), request.otpCode());

        Optional<UserEntity> existingUser = userRepository.findByPhoneNumber(verifiedPhone);
        boolean isNewUser = existingUser.isEmpty();
        UserEntity user;

        if (isNewUser) {
            log.info("Auto-registering new platform user for verified phone {}", maskPhoneNumber(verifiedPhone));
            user = new UserEntity(verifiedPhone, "ACTIVE", "uz");
            user = userRepository.save(user);

            // Assign standard default roles per PRD ID-001/ID-002
            UserRoleEntity registeredRole = new UserRoleEntity(user.getId(), "REGISTERED_USER");
            UserRoleEntity farmerRole = new UserRoleEntity(user.getId(), "FARMER");
            userRoleRepository.save(registeredRole);
            userRoleRepository.save(farmerRole);

            // Initialize minimal user profile
            UserProfileEntity initialProfile = new UserProfileEntity(user.getId(), "", "Toshkent viloyati", "");
            userProfileRepository.save(initialProfile);
        } else {
            user = existingUser.get();
            if ("SUSPENDED".equals(user.getStatus())) {
                log.warn("Login denied for suspended account {}", maskPhoneNumber(verifiedPhone));
                throw new AccountSuspendedException("Hisobingiz maʼmuriyat tomonidan toʻxtatilgan");
            }
        }

        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRoleEntity::getRoleName)
                .toList();

        TokenService.TokenPairResult tokenPair = tokenService.issueTokenPair(
                user.getId(),
                user.getPhoneNumber(),
                roles,
                request.deviceFingerprint(),
                request.userAgent(),
                ipAddress
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getPhoneNumber(),
                user.getStatus(),
                roles
        );

        return new AuthTokenResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.tokenType(),
                tokenPair.expiresInSeconds(),
                isNewUser,
                userSummary
        );
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class})
    public AuthTokenResponse refreshToken(TokenRefreshRequest request, String userAgent, String ipAddress) {
        TokenService.TokenPairResult tokenPair = tokenService.refreshToken(
                request.refreshToken(),
                request.deviceFingerprint(),
                userAgent,
                ipAddress
        );

        return AuthTokenResponse.ofRefresh(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.tokenType(),
                tokenPair.expiresInSeconds()
        );
    }

    @Transactional
    public void logout(LogoutRequest request) {
        tokenService.logout(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUserSummary(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi: " + userId));

        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRoleEntity::getRoleName)
                .toList();

        return new UserSummaryDto(
                user.getId(),
                user.getPhoneNumber(),
                user.getStatus(),
                roles
        );
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
