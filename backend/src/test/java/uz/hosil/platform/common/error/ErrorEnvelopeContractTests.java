package uz.hosil.platform.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.identity.domain.OtpChallengeEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.repository.OtpChallengeRepository;
import uz.hosil.platform.identity.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorEnvelopeContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @Autowired
    private uz.hosil.platform.common.security.TestSmsCaptureProvider smsCaptureProvider;

    @Test
    @DisplayName("401 Unauthorized returns dual-compatible ErrorEnvelope (both $.code and $.error.code)")
    void authenticationRequiredReturnsDualEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                // Flat attributes (backward compatibility)
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/logout"))
                // Nested RFC 7807 error object (docs/05_api_contract.md §2.3)
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.path").value("/api/v1/auth/logout"));
    }

    @Test
    @DisplayName("400 Bad Request on validation failure returns dual-compatible ErrorEnvelope")
    void validationErrorReturnsDualEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", "invalid-phone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @DisplayName("400 Bad Request on malformed JSON payload returns dual-compatible ErrorEnvelope")
    void malformedJsonReturnsDualEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("403 Forbidden for suspended user returns dual-compatible ErrorEnvelope")
    void forbiddenSuspendedUserReturnsDualEnvelope() throws Exception {
        String phone = "+998905554433";
        userRepository.save(new UserEntity(phone, "SUSPENDED", "uz"));

        org.springframework.test.web.servlet.MvcResult req = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", phone))))
                .andExpect(status().isOk()).andReturn();

        Map<?, ?> resp = objectMapper.readValue(req.getResponse().getContentAsString(), Map.class);
        String challengeId = (String) resp.get("challengeId");
        String code = smsCaptureProvider.getLastOtp(phone);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", challengeId,
                                "otpCode", code,
                                "deviceFingerprint", "dev-test",
                                "clientVersion", "1.0.0"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("409 Conflict on locked OTP challenge returns dual-compatible ErrorEnvelope")
    void conflictLockedChallengeReturnsDualEnvelope() throws Exception {
        String phone = "+998909998877";
        OtpChallengeEntity challenge = new OtpChallengeEntity(phone, "hash", "salt", Instant.now().plusSeconds(300));
        challenge.setAttemptsCount(5);
        challenge.setMaxAttempts(5);
        otpChallengeRepository.save(challenge);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeId", challenge.getId().toString(),
                                "otpCode", "123456",
                                "deviceFingerprint", "dev-test",
                                "clientVersion", "1.0.0"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OTP_CHALLENGE_LOCKED"))
                .andExpect(jsonPath("$.error.code").value("OTP_CHALLENGE_LOCKED"));
    }
}
