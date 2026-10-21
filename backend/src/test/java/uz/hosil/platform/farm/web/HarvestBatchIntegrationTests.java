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
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
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
class HarvestBatchIntegrationTests {

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
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserEntity userOwner;
    private String tokenOwner;

    private UserEntity userAgronomist;
    private String tokenAgronomist;

    private UserEntity userWorker;
    private String tokenWorker;

    private UserEntity userOutside;
    private String tokenOutside;

    private FarmEntity farm;
    private FieldEntity field1;
    private CropCycleEntity activeCycle;
    private CropCycleEntity terminatedCycle;

    @BeforeEach
    void setUp() {
        harvestBatchRepository.deleteAll();
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

        // 2. Agronomist
        userAgronomist = userRepository.save(new UserEntity("+998902223344", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userAgronomist.getId(), "Bobur Agronom", "Toshkent viloyati", "Yangiyo'l"));
        tokenAgronomist = jwtTokenProvider.generateAccessToken(userAgronomist.getId(), userAgronomist.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 3. Worker (Read/Write activity only, no RECORD_HARVEST)
        userWorker = userRepository.save(new UserEntity("+998903334455", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userWorker.getId(), "Hamid Ishchi", "Toshkent viloyati", "Yangiyo'l"));
        tokenWorker = jwtTokenProvider.generateAccessToken(userWorker.getId(), userWorker.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 4. Outside user
        userOutside = userRepository.save(new UserEntity("+998909998877", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userOutside.getId(), "Begona User", "Samarqand viloyati", "Tayloq"));
        tokenOutside = jwtTokenProvider.generateAccessToken(userOutside.getId(), userOutside.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 5. Farm & Memberships
        farm = farmRepository.save(new FarmEntity(userOwner.getId(), "Navbahor Agro", "Toshkent viloyati", "Yangiyo'l", new BigDecimal("25.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userOwner.getId(), "OWNER", "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET", "ACTIVE"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userAgronomist.getId(), "AGRONOMIST", "READ,WRITE_ACTIVITY,RECORD_HARVEST", "ACTIVE"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userWorker.getId(), "WORKER", "READ,WRITE_ACTIVITY", "ACTIVE"));

        // 6. Fields & Crop Cycles
        field1 = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "1-Dala", new BigDecimal("10.00"), "HECTARE", null, null));

        activeCycle = cropCycleRepository.save(new CropCycleEntity(
                UUID.randomUUID(), farm.getId(), field1.getId(), "Pomidor", "Pink Paradise",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 1), "ACTIVE"
        ));

        terminatedCycle = cropCycleRepository.save(new CropCycleEntity(
                UUID.randomUUID(), farm.getId(), field1.getId(), "Bodring", "Orzu",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 1), "TERMINATED"
        ));
    }

    @Test
    void recordHarvestBatch_AuthorizedOwner_Success() throws Exception {
        UUID batchId = UUID.randomUUID();
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                batchId,
                activeCycle.getId(),
                "Pomidor (Pink Paradise)",
                "FIRST_CLASS",
                "5000.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                "1-Omborxona, A-seksiya",
                "client-batch-001"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(batchId.toString()))
                .andExpect(jsonPath("$.farmId").value(farm.getId().toString()))
                .andExpect(jsonPath("$.cropCycleId").value(activeCycle.getId().toString()))
                .andExpect(jsonPath("$.cropType").value("Pomidor (Pink Paradise)"))
                .andExpect(jsonPath("$.qualityGrade").value("FIRST_CLASS"))
                .andExpect(jsonPath("$.recordedQuantity").value("5000.0"))
                .andExpect(jsonPath("$.unallocatedQuantity").value("5000.0"))
                .andExpect(jsonPath("$.unitCode").value("KG"))
                .andExpect(jsonPath("$.status").value("RECORDED"));

        HarvestBatchEntity saved = harvestBatchRepository.findById(batchId).orElseThrow();
        assertEquals(new BigDecimal("5000.000"), saved.getRecordedQuantity());
        assertEquals(new BigDecimal("5000.000"), saved.getUnallocatedQuantity());
        assertEquals("RECORDED", saved.getStatus());
    }

    @Test
    void recordHarvestBatch_AuthorizedAgronomist_Success() throws Exception {
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "PREMIUM",
                "1500.000",
                "KG",
                LocalDate.of(2026, 7, 3),
                null,
                "client-batch-agronomist-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenAgronomist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qualityGrade").value("PREMIUM"))
                .andExpect(jsonPath("$.recordedQuantity").value("1500.0"));
    }

    @Test
    void recordHarvestBatch_WorkerWithoutPermission_Returns403() throws Exception {
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "1000.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                null,
                "client-batch-worker-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenWorker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordHarvestBatch_CrossFarmAccess_Returns403() throws Exception {
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "1000.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                null,
                "client-batch-outside-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOutside)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordHarvestBatch_TerminatedCycle_Returns409Conflict() throws Exception {
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                terminatedCycle.getId(),
                "Bodring",
                "STANDARD",
                "500.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                null,
                "client-batch-terminated-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void recordHarvestBatch_DateBeforePlanting_Returns409Conflict() throws Exception {
        // activeCycle planted on 2026-03-01, harvestDate is 2026-02-15
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "500.000",
                "KG",
                LocalDate.of(2026, 2, 15),
                null,
                "client-batch-early-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void recordHarvestBatch_InvalidUnit_Returns409Conflict() throws Exception {
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "500.000",
                "UNKNOWN_UNIT_XYZ",
                LocalDate.of(2026, 7, 2),
                null,
                "client-batch-bad-unit-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void recordHarvestBatch_ZeroOrNegativeQuantity_Returns409Conflict() throws Exception {
        CreateHarvestBatchRequest zeroReq = new CreateHarvestBatchRequest(
                UUID.randomUUID(),
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "0.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                null,
                "client-batch-zero-1"
        );

        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void recordHarvestBatch_IdempotentReplay_ReturnsExistingRecord() throws Exception {
        UUID batchId = UUID.randomUUID();
        CreateHarvestBatchRequest req = new CreateHarvestBatchRequest(
                batchId,
                activeCycle.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "2500.000",
                "KG",
                LocalDate.of(2026, 7, 2),
                "Ombor 2",
                "idemp-batch-101"
        );

        // 1. Initial creation -> 201 Created
        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(batchId.toString()));

        // 2. Exact retry with same clientBatchId and ID -> 200 OK + IDEMPOTENT_HIT
        mockMvc.perform(post("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andExpect(jsonPath("$.id").value(batchId.toString()));

        assertEquals(1, harvestBatchRepository.findAll().size());
    }

    @Test
    void getHarvestBatch_OwnerSuccess_AndOutsideUserForbidden() throws Exception {
        UUID batchId = UUID.randomUUID();
        HarvestBatchEntity batch = new HarvestBatchEntity(
                batchId, farm.getId(), activeCycle.getId(), "Pomidor", "FIRST_CLASS",
                new BigDecimal("3000.000"), new BigDecimal("3000.000"), "KG",
                LocalDate.of(2026, 7, 2), "Ombor 1", "RECORDED", "b1", null, 1L
        );
        harvestBatchRepository.save(batch);

        // 1. Owner gets batch -> 200 OK
        mockMvc.perform(get("/api/v1/harvest-batches/" + batchId)
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batchId.toString()))
                .andExpect(jsonPath("$.unallocatedQuantity").value("3000.0"));

        // 2. Outside user attempts to get batch -> 403 Forbidden (Privacy protection)
        mockMvc.perform(get("/api/v1/harvest-batches/" + batchId)
                        .header("Authorization", "Bearer " + tokenOutside))
                .andExpect(status().isForbidden());
    }

    @Test
    void listHarvestBatches_OwnerSuccess_AndOutsideUserForbidden() throws Exception {
        HarvestBatchEntity batch = new HarvestBatchEntity(
                UUID.randomUUID(), farm.getId(), activeCycle.getId(), "Pomidor", "FIRST_CLASS",
                new BigDecimal("1000.000"), new BigDecimal("1000.000"), "KG",
                LocalDate.of(2026, 7, 2), "Ombor 1", "RECORDED", "b-list-1", null, 1L
        );
        harvestBatchRepository.save(batch);

        // 1. Owner lists -> 200 OK
        mockMvc.perform(get("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 2. Outside user lists -> 403 Forbidden
        mockMvc.perform(get("/api/v1/farms/" + farm.getId() + "/harvest-batches")
                        .header("Authorization", "Bearer " + tokenOutside))
                .andExpect(status().isForbidden());
    }
}
