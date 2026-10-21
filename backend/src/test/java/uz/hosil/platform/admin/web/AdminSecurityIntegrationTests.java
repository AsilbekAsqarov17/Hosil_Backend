package uz.hosil.platform.admin.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        UserEntity adminUser = userRepository.save(new UserEntity("+998901111199", "ACTIVE", "uz"));
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getPhoneNumber(), List.of("REGISTERED_USER", "ADMINISTRATOR"));

        UserEntity regularUser = userRepository.save(new UserEntity("+998902222288", "ACTIVE", "uz"));
        regularUserToken = jwtTokenProvider.generateAccessToken(regularUser.getId(), regularUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));
    }

    @Test
    @DisplayName("Unauthenticated request to /admin must be rejected with 401 Unauthorized")
    void unauthenticatedAdminAccessReturns401() throws Exception {
        mockMvc.perform(get("/admin")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Unauthenticated request to /admin/dashboard must be rejected with 401 Unauthorized")
    void unauthenticatedAdminDashboardReturns401() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Unauthenticated request to root path / must NOT expose admin dashboard")
    void unauthenticatedRootPathDoesNotExposeDashboard() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Authenticated non-admin user accessing /admin must be rejected with 403 Forbidden")
    void nonAdminUserReturns403() throws Exception {
        mockMvc.perform(get("/admin")
                .header("Authorization", "Bearer " + regularUserToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    @DisplayName("Authenticated administrator accessing /admin must be granted 200 OK")
    void administratorAccessReturns200() throws Exception {
        mockMvc.perform(get("/admin")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("appName", "pendingVerificationsCount", "pendingListingsCount"));
    }

    @Test
    @DisplayName("Public actuator health endpoint remains accessible without credentials")
    void actuatorHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
