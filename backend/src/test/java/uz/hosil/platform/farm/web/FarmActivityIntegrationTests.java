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
import uz.hosil.platform.farm.domain.FarmActivityEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmActivityRepository;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmActivityIntegrationTests {

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
    private FarmActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserEntity userOwner;
    private String tokenOwner;

    private UserEntity userWorkerNoWrite;
    private String tokenWorkerNoWrite;

    private UserEntity userOutside;
    private String tokenOutside;

    private FarmEntity farm;
    private FieldEntity field1;
    private FieldEntity field2;
    private CropCycleEntity cycle1;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        membershipRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Owner
        userOwner = userRepository.save(new UserEntity("+998901112233", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userOwner.getId(), "Ali Fermer", "Toshkent viloyati", "Yangiyo'l"));
        tokenOwner = jwtTokenProvider.generateAccessToken(userOwner.getId(), userOwner.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));

        // 2. Worker without write permission
        userWorkerNoWrite = userRepository.save(new UserEntity("+998902223344", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userWorkerNoWrite.getId(), "Hasan Ishchi", "Toshkent viloyati", "Yangiyo'l"));
        tokenWorkerNoWrite = jwtTokenProvider.generateAccessToken(userWorkerNoWrite.getId(), userWorkerNoWrite.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 3. Unrelated outside user
        userOutside = userRepository.save(new UserEntity("+998909998877", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userOutside.getId(), "Begona User", "Samarqand viloyati", "Tayloq"));
        tokenOutside = jwtTokenProvider.generateAccessToken(userOutside.getId(), userOutside.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 4. Base Farm holding
        farm = farmRepository.save(new FarmEntity(userOwner.getId(), "Beshqo'rg'on Agro", "Toshkent viloyati", "Yangiyo'l", new BigDecimal("15.50"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userOwner.getId(), "OWNER", "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET", "ACTIVE"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userWorkerNoWrite.getId(), "WORKER", "READ", "ACTIVE"));

        // 5. Fields & Crop Cycle
        field1 = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "1-Dala", new BigDecimal("5.00"), "HECTARE", null, null));
        field2 = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "2-Dala", new BigDecimal("3.50"), "HECTARE", null, null));

        cycle1 = cropCycleRepository.save(new CropCycleEntity(
                UUID.randomUUID(), farm.getId(), field1.getId(), "Pomidor", "Pink Paradise",
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 7, 1), "ACTIVE"
        ));
    }

    @Test
    void recordActivity_AuthorizedOwner_Success() throws Exception {
        UUID activityId = UUID.randomUUID();
        CreateFarmActivityRequest req = new CreateFarmActivityRequest(
                activityId,
                cycle1.getId(),
                field1.getId(),
                "FERTILIZING",
                Instant.now(),
                "25.500",
                "KG",
                "Kaliy sulfat o'g'itlash o'tkazildi",
                "mut-1001",
                1L
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(activityId.toString()))
                .andExpect(jsonPath("$.cropCycleId").value(cycle1.getId().toString()))
                .andExpect(jsonPath("$.fieldId").value(field1.getId().toString()))
                .andExpect(jsonPath("$.activityType").value("FERTILIZING"))
                .andExpect(jsonPath("$.quantity").value("25.500"))
                .andExpect(jsonPath("$.unit").value("KG"))
                .andExpect(jsonPath("$.syncVersion").value(1));

        List<FarmActivityEntity> all = activityRepository.findAll();
        assertEquals(1, all.size());
        assertEquals(userOwner.getId(), all.get(0).getRecordedByUserId());
    }

    @Test
    void recordActivity_WorkerWithoutWritePermission_Returns403() throws Exception {
        CreateFarmActivityRequest req = new CreateFarmActivityRequest(
                UUID.randomUUID(),
                cycle1.getId(),
                field1.getId(),
                "IRRIGATION",
                Instant.now(),
                null,
                null,
                "Suv berildi",
                "mut-worker-1",
                1L
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenWorkerNoWrite)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordActivity_CrossFarmAccess_Returns403() throws Exception {
        CreateFarmActivityRequest req = new CreateFarmActivityRequest(
                UUID.randomUUID(),
                cycle1.getId(),
                field1.getId(),
                "WEEDING",
                Instant.now(),
                null,
                null,
                "O'toq qilindi",
                "mut-out-1",
                1L
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOutside)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordActivity_CycleDoesNotMatchField_Returns409Conflict() throws Exception {
        // cycle1 is on field1, but request specifies field2
        CreateFarmActivityRequest req = new CreateFarmActivityRequest(
                UUID.randomUUID(),
                cycle1.getId(),
                field2.getId(),
                "IRRIGATION",
                Instant.now(),
                null,
                null,
                "Noto'g'ri bog'lanish",
                "mut-mismatch-1",
                1L
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void recordActivity_IdempotentReplay_ReturnsExistingRecord() throws Exception {
        UUID activityId = UUID.randomUUID();
        CreateFarmActivityRequest req = new CreateFarmActivityRequest(
                activityId,
                cycle1.getId(),
                field1.getId(),
                "SCOUTING",
                Instant.now(),
                null,
                null,
                "Dala ko'rigi",
                "mut-idemp-1",
                1L
        );

        // First submission -> 201 Created
        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(activityId.toString()));

        // Exact retry with same clientMutationId and ID -> returns existing without creating duplicate
        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activityId.toString()));

        assertEquals(1, activityRepository.findAll().size());
    }

    @Test
    void listActivities_FiltersAndPagination_Success() throws Exception {
        // Create 2 activities
        activityRepository.save(new FarmActivityEntity(UUID.randomUUID(), farm.getId(), cycle1.getId(), field1.getId(), userOwner.getId(), "IRRIGATION", Instant.now(), null, null, "1", 1L, "m1"));
        activityRepository.save(new FarmActivityEntity(UUID.randomUUID(), farm.getId(), null, field2.getId(), userOwner.getId(), "PLANTING", Instant.now(), null, null, "2", 1L, "m2"));

        // List all for farm
        mockMvc.perform(get("/api/v1/farms/" + farm.getId() + "/activities")
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Filter by field1
        mockMvc.perform(get("/api/v1/farms/" + farm.getId() + "/activities?fieldId=" + field1.getId())
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].activityType").value("IRRIGATION"));
    }

    @Test
    void batchSync_ExceedLimit_Returns409Conflict() throws Exception {
        List<ActivityMutationItem> list = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            list.add(new ActivityMutationItem(
                    "mut-" + i,
                    "CREATE",
                    UUID.randomUUID(),
                    1L,
                    new CreateFarmActivityRequest(null, null, field1.getId(), "INSPECTION", Instant.now(), null, null, null, null, 1L)
            ));
        }

        ActivityBatchSyncRequest request = new ActivityBatchSyncRequest("sync-batch-overflow", list);

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities/batch-sync")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // validation catches @Size(max=50)
    }

    @Test
    void batchSync_MixedMutations_DeterministicPartialOutcomes() throws Exception {
        UUID validId = UUID.randomUUID();
        UUID invalidFieldId = UUID.randomUUID();

        List<ActivityMutationItem> mutations = List.of(
                // 1. Valid CREATE
                new ActivityMutationItem(
                        "mut-batch-1",
                        "CREATE",
                        validId,
                        1L,
                        new CreateFarmActivityRequest(validId, cycle1.getId(), field1.getId(), "FERTILIZING", Instant.now(), "10.000", "KG", "OK", "mut-batch-1", 1L)
                ),
                // 2. Invalid CREATE (field does not exist)
                new ActivityMutationItem(
                        "mut-batch-2",
                        "CREATE",
                        UUID.randomUUID(),
                        1L,
                        new CreateFarmActivityRequest(null, null, invalidFieldId, "IRRIGATION", Instant.now(), null, null, "Fail", "mut-batch-2", 1L)
                )
        );

        ActivityBatchSyncRequest batchReq = new ActivityBatchSyncRequest("batch-mixed-001", mutations);

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities/batch-sync")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientBatchSyncId").value("batch-mixed-001"))
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].clientMutationId").value("mut-batch-1"))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].serverSyncVersion").value(1))
                .andExpect(jsonPath("$.results[1].clientMutationId").value("mut-batch-2"))
                .andExpect(jsonPath("$.results[1].status").value("REJECTED"));

        // Only the valid item should be persisted in DB
        List<FarmActivityEntity> persisted = activityRepository.findAll();
        assertEquals(1, persisted.size());
        assertEquals(validId, persisted.get(0).getId());
    }

    @Test
    void batchSync_OptimisticConcurrencyConflict_Detected() throws Exception {
        UUID entityId = UUID.randomUUID();
        // Server creates activity and increments syncVersion to 3
        FarmActivityEntity entity = new FarmActivityEntity(
                entityId, farm.getId(), cycle1.getId(), field1.getId(), userOwner.getId(),
                "IRRIGATION", Instant.now(), null, null, "Server update", 3L, "init-mutation"
        );
        activityRepository.save(entity);

        // Client attempts to UPDATE using stale baseSyncVersion = 1
        ActivityMutationItem staleUpdate = new ActivityMutationItem(
                "mut-stale-update",
                "UPDATE",
                entityId,
                1L, // stale client version
                new CreateFarmActivityRequest(entityId, cycle1.getId(), field1.getId(), "FERTILIZING", Instant.now(), "50.000", "KG", "Stale overwrite", "mut-stale-update", 1L)
        );

        ActivityBatchSyncRequest request = new ActivityBatchSyncRequest("batch-conflict-001", List.of(staleUpdate));

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/activities/batch-sync")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].clientMutationId").value("mut-stale-update"))
                .andExpect(jsonPath("$.results[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].serverSyncVersion").value(3));

        // Ensure server record was NOT overwritten with stale client payload
        FarmActivityEntity untouched = activityRepository.findById(entityId).orElseThrow();
        assertEquals("IRRIGATION", untouched.getActivityType());
        assertEquals(3L, untouched.getSyncVersion());
    }
}
