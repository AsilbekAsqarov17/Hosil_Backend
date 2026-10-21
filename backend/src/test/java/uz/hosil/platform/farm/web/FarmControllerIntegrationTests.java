package uz.hosil.platform.farm.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FarmMembershipRepository membershipRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserEntity userA;
    private String tokenA;

    private UserEntity userB;
    private String tokenB;

    @BeforeEach
    void setUp() {
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        membershipRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        userA = new UserEntity("+998901112233", "ACTIVE", "uz");
        userA = userRepository.save(userA);
        userProfileRepository.save(new UserProfileEntity(userA.getId(), "Ali Valiyev", "Toshkent viloyati", "Yangiyo'l"));
        tokenA = jwtTokenProvider.generateAccessToken(userA.getId(), userA.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));

        userB = new UserEntity("+998904445566", "ACTIVE", "uz");
        userB = userRepository.save(userB);
        userProfileRepository.save(new UserProfileEntity(userB.getId(), "Vali Aliyev", "Samarqand viloyati", "Tayloq"));
        tokenB = jwtTokenProvider.generateAccessToken(userB.getId(), userB.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));
    }

    @Test
    void createFarm_AuthenticatedFarmer_Success() throws Exception {
        CreateFarmRequest request = new CreateFarmRequest(
                "Oltin Voha Agro",
                "Samarqand viloyati",
                "Tayloq tumani",
                "12.50",
                "FERMER"
        );

        mockMvc.perform(post("/api/v1/farms")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerUserId").value(userA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Oltin Voha Agro"))
                .andExpect(jsonPath("$.totalAreaHa").value("12.50"))
                .andExpect(jsonPath("$.legalType").value("FERMER"));

        List<FarmEntity> farms = farmRepository.findAll();
        assertEquals(1, farms.size());
        assertEquals("Oltin Voha Agro", farms.get(0).getName());

        List<FarmMembershipEntity> memberships = membershipRepository.findByFarmId(farms.get(0).getId());
        assertEquals(1, memberships.size());
        assertEquals("OWNER", memberships.get(0).getRole());
        assertEquals(userA.getId(), memberships.get(0).getUserId());
    }

    @Test
    void createFarm_Unauthenticated_Returns401() throws Exception {
        CreateFarmRequest request = new CreateFarmRequest(
                "Oltin Voha Agro",
                "Samarqand viloyati",
                "Tayloq tumani",
                "12.50",
                "FERMER"
        );

        mockMvc.perform(post("/api/v1/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listFarms_TenantIsolation_ReturnsOnlyOwnFarms() throws Exception {
        // Create Farm A for User A
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ,WRITE_ACTIVITY", "ACTIVE"));

        // Create Farm B for User B
        FarmEntity farmB = farmRepository.save(new FarmEntity(userB.getId(), "Farm B", "Samarqand", "Tayloq", new BigDecimal("20.00"), "DEHQON"));
        membershipRepository.save(new FarmMembershipEntity(farmB.getId(), userB.getId(), "OWNER", "READ,WRITE_ACTIVITY", "ACTIVE"));

        // User A lists farms -> should only see Farm A
        mockMvc.perform(get("/api/v1/farms")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(farmA.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("Farm A"));
    }

    @Test
    void getFarm_CrossFarmAccess_Returns403Forbidden() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        // User B attempts to access Farm A
        mockMvc.perform(get("/api/v1/farms/" + farmA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void updateFarm_OwnerCanUpdate_WorkerCannot() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        // User B is added as WORKER
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userB.getId(), "WORKER", "READ", "ACTIVE"));

        UpdateFarmRequest updateRequest = new UpdateFarmRequest("Farm A Updated", "15.00", "MCHJ");

        // Worker attempts update -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/farms/" + farmA.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // Owner attempts update -> 200 OK
        mockMvc.perform(patch("/api/v1/farms/" + farmA.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Farm A Updated"))
                .andExpect(jsonPath("$.totalAreaHa").value("15.00"))
                .andExpect(jsonPath("$.legalType").value("MCHJ"));
    }

    @Test
    void addMember_OwnerSuccess_AndDuplicateConflict() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        AddFarmMemberRequest addRequest = new AddFarmMemberRequest(userB.getPhoneNumber(), "AGRONOMIST", "READ,WRITE_ACTIVITY");

        // Owner invites User B
        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGRONOMIST"))
                .andExpect(jsonPath("$.userId").value(userB.getId().toString()))
                .andExpect(jsonPath("$.phoneNumber").value(userB.getPhoneNumber()))
                .andExpect(jsonPath("$.fullName").value("Vali Aliyev"));

        // Attempting to add same user again -> 409 Conflict
        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void createField_Success_AndValidation() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        CreateFieldRequest fieldReq = new CreateFieldRequest(
                UUID.randomUUID(),
                "1-Dala Issiqxona",
                "1.50",
                "HECTARE",
                "{\"type\": \"Polygon\"}",
                "Drip irrigation"
        );

        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/fields")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fieldReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fieldReq.id().toString()))
                .andExpect(jsonPath("$.name").value("1-Dala Issiqxona"))
                .andExpect(jsonPath("$.area").value("1.50"))
                .andExpect(jsonPath("$.areaUnit").value("HECTARE"));

        // Invalid Area Unit
        CreateFieldRequest invalidUnitReq = new CreateFieldRequest(
                UUID.randomUUID(),
                "2-Dala",
                "2.00",
                "INVALID_UNIT",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/fields")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUnitReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cropCycle_LifecycleStateMachineAndInvariants() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farmA.getId(), "1-Dala", new BigDecimal("1.50"), "HECTARE", null, null));

        // 1. Invalid dates: expectedHarvestDate before plantingDate -> 409 Conflict
        CreateCropCycleRequest invalidDatesReq = new CreateCropCycleRequest(
                UUID.randomUUID(),
                field.getId(),
                "Pomidor",
                "Pink Paradise",
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 4, 10)
        );

        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/crop-cycles")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDatesReq)))
                .andExpect(status().isConflict());

        // 2. Valid creation
        CreateCropCycleRequest validReq = new CreateCropCycleRequest(
                UUID.randomUUID(),
                field.getId(),
                "Pomidor",
                "Pink Paradise",
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 7, 1)
        );

        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/crop-cycles")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cropType").value("Pomidor"));

        UUID cycleId = validReq.id();

        // 3. State transition: ACTIVE -> HARVESTED
        UpdateCropCycleRequest harvestReq = new UpdateCropCycleRequest(null, LocalDate.of(2026, 7, 5), "HARVESTED");

        mockMvc.perform(patch("/api/v1/farms/" + farmA.getId() + "/crop-cycles/" + cycleId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(harvestReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HARVESTED"))
                .andExpect(jsonPath("$.actualHarvestDate").value("2026-07-05"));

        // 4. Terminal state invariant: Attempting to transition from HARVESTED back to ACTIVE -> 409 Conflict
        UpdateCropCycleRequest terminalViolationReq = new UpdateCropCycleRequest(null, null, "ACTIVE");

        mockMvc.perform(patch("/api/v1/farms/" + farmA.getId() + "/crop-cycles/" + cycleId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(terminalViolationReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void cropCycle_FieldBelongsToOtherFarm_Returns404NotFound() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ", "ACTIVE"));

        FarmEntity farmB = farmRepository.save(new FarmEntity(userB.getId(), "Farm B", "Samarqand", "Tayloq", new BigDecimal("20.00"), "DEHQON"));
        membershipRepository.save(new FarmMembershipEntity(farmB.getId(), userB.getId(), "OWNER", "READ", "ACTIVE"));

        // Field created on Farm B
        FieldEntity fieldB = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farmB.getId(), "Farm B Field", new BigDecimal("5.00"), "HECTARE", null, null));

        // Attempting to create crop cycle on Farm A with Field B
        CreateCropCycleRequest crossFarmReq = new CreateCropCycleRequest(
                UUID.randomUUID(),
                fieldB.getId(),
                "Bug'doy",
                "Krasnodar",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 8, 1)
        );

        mockMvc.perform(post("/api/v1/farms/" + farmA.getId() + "/crop-cycles")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossFarmReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeMembership_OwnerSuccess_AndSoleOwnerConflict_AndNonOwnerForbidden() throws Exception {
        FarmEntity farmA = farmRepository.save(new FarmEntity(userA.getId(), "Farm A", "Toshkent", "Yangiyo'l", new BigDecimal("10.00"), "FERMER"));
        FarmMembershipEntity ownerMembership = membershipRepository.save(
                new FarmMembershipEntity(farmA.getId(), userA.getId(), "OWNER", "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET", "ACTIVE")
        );
        FarmMembershipEntity memberMembership = membershipRepository.save(
                new FarmMembershipEntity(farmA.getId(), userB.getId(), "AGRONOMIST", "READ,WRITE_ACTIVITY,RECORD_HARVEST", "ACTIVE")
        );

        // 1. Non-owner (User B) attempts to revoke -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/farms/" + farmA.getId() + "/members/" + memberMembership.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 2. Owner attempts to revoke sole owner -> 409 Conflict
        mockMvc.perform(delete("/api/v1/farms/" + farmA.getId() + "/members/" + ownerMembership.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 3. Owner revokes agronomist -> 204 No Content
        mockMvc.perform(delete("/api/v1/farms/" + farmA.getId() + "/members/" + memberMembership.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // Verify status in DB is REVOKED
        FarmMembershipEntity updatedMember = membershipRepository.findById(memberMembership.getId()).orElseThrow();
        assertEquals("REVOKED", updatedMember.getStatus());
    }
}

