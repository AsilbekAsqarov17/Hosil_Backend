package uz.hosil.platform.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uz.hosil.platform.common.security.TestSmsCaptureProvider;
import uz.hosil.platform.identity.domain.OtpChallengeEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.repository.AuthSessionRepository;
import uz.hosil.platform.identity.repository.OtpChallengeRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.identity.repository.UserRoleRepository;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestSmsCaptureProvider smsCaptureProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @BeforeEach
    void setUp() {
        authSessionRepository.deleteAll();
        userRoleRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
        otpChallengeRepository.deleteAll();
        smsCaptureProvider.clear();
    }

    @Test
    void requestOtp_ValidPhone_ReturnsChallengeDispatchedAndSendsSms() throws Exception {
        String phone = "+998901234567";
        OtpRequest request = new OtpRequest(phone);

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(180))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));

        String capturedOtp = smsCaptureProvider.getLastOtp(phone);
        assertNotNull(capturedOtp);
        assertEquals(6, capturedOtp.length());
        assertTrue(capturedOtp.matches("^[0-9]{6}$"));
    }

    @Test
    void requestOtp_InvalidPhoneFormat_Returns400BadRequest() throws Exception {
        OtpRequest request = new OtpRequest("invalid-phone");

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requestOtp_ResendWithinCooldown_Returns429TooManyRequests() throws Exception {
        String phone = "+998907778891";
        OtpRequest request = new OtpRequest(phone);

        // First request succeeds
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));

        // Immediate second request within 60s cooldown fails with 429
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void requestOtp_ExceedSlidingWindowLimit_Returns429TooManyRequests() throws Exception {
        String phone = "+998907778892";
        OtpRequest request = new OtpRequest(phone);

        // Pre-populate 3 challenges spaced 70 seconds apart so cooldown is satisfied, but window count is reached
        Instant now = Instant.now();
        for (int i = 3; i >= 1; i--) {
            OtpChallengeEntity challenge = new OtpChallengeEntity(phone, "hash" + i, "salt" + i, now.plusSeconds(180));
            challenge.setCreatedAt(now.minusSeconds(i * 70));
            otpChallengeRepository.save(challenge);
        }

        // 4th request within 5 minutes triggers HTTP 429 due to 3-request window limit
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void verifyOtp_ValidOtpNewUser_AutoRegistersUserAndIssuesTokens() throws Exception {
        String phone = "+998911112233";

        // 1. Request OTP
        MvcResult reqResult = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk())
                .andReturn();

        OtpChallengeResponse challengeResp = objectMapper.readValue(
                reqResult.getResponse().getContentAsString(), OtpChallengeResponse.class
        );
        String otpCode = smsCaptureProvider.getLastOtp(phone);

        // 2. Verify OTP
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest(
                challengeResp.challengeId(),
                otpCode,
                "samsung-galaxy-s24",
                "HosilFarm/1.0.0 (Android 14)"
        );

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.user.phoneNumber").value(phone))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.roles", hasItems("REGISTERED_USER", "FARMER")));

        // Verify database state
        UserEntity createdUser = userRepository.findByPhoneNumber(phone).orElseThrow();
        assertEquals("ACTIVE", createdUser.getStatus());
        assertEquals("uz", createdUser.getPreferredLanguage());
        assertTrue(userProfileRepository.findByUserId(createdUser.getId()).isPresent());
        assertEquals(2, userRoleRepository.findByUserId(createdUser.getId()).size());
        assertEquals(1, authSessionRepository.findByUserIdAndIsRevokedFalse(createdUser.getId()).size());
    }

    @Test
    void verifyOtp_ExistingUser_AuthenticatesWithoutDuplicateRegistration() throws Exception {
        String phone = "+998933334455";

        // Register first time
        MvcResult req1 = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch1 = objectMapper.readValue(req1.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code1 = smsCaptureProvider.getLastOtp(phone);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch1.challengeId(), code1, "device1", "agent1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));

        assertEquals(1, userRepository.count());

        // Clear consumed OTP challenge to simulate returning user login session on a later occasion
        otpChallengeRepository.deleteAll();

        // Login second time
        MvcResult req2 = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch2 = objectMapper.readValue(req2.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code2 = smsCaptureProvider.getLastOtp(phone);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch2.challengeId(), code2, "device1", "agent1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false));

        // Still only 1 user in the database
        assertEquals(1, userRepository.count());
    }

    @Test
    void verifyOtp_IncorrectCode_IncrementsAttemptsAndReturns400() throws Exception {
        String phone = "+998944445566";
        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);

        OtpVerifyRequest wrongVerify = new OtpVerifyRequest(ch.challengeId(), "999999", "dev", "agent");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongVerify)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"))
                .andExpect(jsonPath("$.message", containsString("Qolgan urinishlar: 4")));
    }

    @Test
    void verifyOtp_MaxAttemptsExceeded_LocksChallengeWith409Conflict() throws Exception {
        String phone = "+998955556677";
        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);

        OtpVerifyRequest wrongVerify = new OtpVerifyRequest(ch.challengeId(), "000000", "dev", "agent");

        // Fail 4 times (400)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongVerify)))
                    .andExpect(status().isBadRequest());
        }

        // 5th failed attempt locks challenge with HTTP 409
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongVerify)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OTP_CHALLENGE_LOCKED"));
    }

    @Test
    void refreshToken_ValidToken_RotatesTokenPair() throws Exception {
        String phone = "+998966667788";

        // 1. Authenticate to get initial token pair
        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code = smsCaptureProvider.getLastOtp(phone);

        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch.challengeId(), code, "fingerprint-1", "agent-1"))))
                .andExpect(status().isOk()).andReturn();

        AuthTokenResponse initialTokens = objectMapper.readValue(
                authResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        // 2. Refresh token pair
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest(initialTokens.refreshToken(), "fingerprint-1");
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthTokenResponse rotatedTokens = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        assertNotEquals(initialTokens.refreshToken(), rotatedTokens.refreshToken());
        assertNotEquals(initialTokens.accessToken(), rotatedTokens.accessToken());

        // 3. Attempting to replay old refresh token triggers 401 reuse detection
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_ValidSession_RevokesSessionAndDisablesRefresh() throws Exception {
        String phone = "+998977778899";

        // Authenticate
        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code = smsCaptureProvider.getLastOtp(phone);

        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch.challengeId(), code, "f1", "a1"))))
                .andExpect(status().isOk()).andReturn();

        AuthTokenResponse tokens = objectMapper.readValue(
                authResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        // Logout with Bearer token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        // Refreshing with logged out token fails with 401
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRefreshRequest(tokens.refreshToken(), "f1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Contract verification: Stateless access token remains cryptographically authentic
        // for its 15-minute window; mobile client purges it upon logout while server enforces session revocation.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void protectedEndpoint_WithValidBearerToken_Returns200AndUserProfile() throws Exception {
        String phone = "+998988889900";

        // Authenticate
        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code = smsCaptureProvider.getLastOtp(phone);

        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch.challengeId(), code, "f1", "a1"))))
                .andExpect(status().isOk()).andReturn();

        AuthTokenResponse tokens = objectMapper.readValue(
                authResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        // Access protected /users/me
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(phone))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles", hasItems("REGISTERED_USER", "FARMER")));
    }

    @Test
    void verifyOtp_SuspendedAccount_Returns403Forbidden() throws Exception {
        String phone = "+998999990011";

        // Pre-create suspended user
        UserEntity suspendedUser = new UserEntity(phone, "SUSPENDED", "uz");
        userRepository.save(suspendedUser);

        MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk()).andReturn();
        OtpChallengeResponse ch = objectMapper.readValue(req.getResponse().getContentAsString(), OtpChallengeResponse.class);
        String code = smsCaptureProvider.getLastOtp(phone);

        // Verification must be rejected with 403 Forbidden
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(ch.challengeId(), code, "f1", "a1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }
}
