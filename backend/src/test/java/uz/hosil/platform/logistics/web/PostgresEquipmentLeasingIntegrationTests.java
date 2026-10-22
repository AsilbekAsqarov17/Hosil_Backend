package uz.hosil.platform.logistics.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.logistics.domain.EquipmentListingEntity;
import uz.hosil.platform.logistics.domain.LeasingApplicationEntity;
import uz.hosil.platform.logistics.domain.LeasingContractEntity;
import uz.hosil.platform.logistics.repository.EquipmentListingRepository;
import uz.hosil.platform.logistics.repository.LeasingApplicationRepository;
import uz.hosil.platform.logistics.repository.LeasingContractRepository;
import uz.hosil.platform.logistics.repository.LeasingRepaymentScheduleRepository;
import uz.hosil.platform.logistics.service.MockSandboxLeasingPartnerGateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PostgresEquipmentLeasingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private EquipmentListingRepository equipmentRepository;

    @Autowired
    private LeasingApplicationRepository applicationRepository;

    @Autowired
    private LeasingContractRepository contractRepository;

    @Autowired
    private LeasingRepaymentScheduleRepository repaymentRepository;

    @Autowired
    private MockSandboxLeasingPartnerGateway sandboxGateway;

    private UserEntity supplierUser;
    private String supplierToken;

    private UserEntity farmerUser;
    private String farmerToken;

    private UserEntity strangerUser;
    private String strangerToken;

    @BeforeEach
    void setUp() {
        sandboxGateway.reset();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        supplierUser = userRepository.save(new UserEntity("+99890" + suffix.substring(0, 7), "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(supplierUser.getId(), "Agromash Servis MCHJ", "Toshkent viloyati", "Chirchiq"));
        supplierToken = jwtTokenProvider.generateAccessToken(supplierUser.getId(), supplierUser.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        String suffix2 = UUID.randomUUID().toString().substring(0, 8);
        farmerUser = userRepository.save(new UserEntity("+99891" + suffix2.substring(0, 7), "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(farmerUser.getId(), "Bahodir Dehqon", "Samarqand viloyati", "Pastdargʻom"));
        farmerToken = jwtTokenProvider.generateAccessToken(farmerUser.getId(), farmerUser.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));

        String suffix3 = UUID.randomUUID().toString().substring(0, 8);
        strangerUser = userRepository.save(new UserEntity("+99893" + suffix3.substring(0, 7), "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(strangerUser.getId(), "Begona Foydalanuvchi", "Fargʻona viloyati", "Qoʻqon"));
        strangerToken = jwtTokenProvider.generateAccessToken(strangerUser.getId(), strangerUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));
    }

    private EquipmentListingEntity createSampleTractor(String title, BigDecimal marketValue) {
        return equipmentRepository.save(new EquipmentListingEntity(
                supplierUser.getId(),
                "TRACTOR",
                title,
                "Belarus 82.1",
                "Minsk Tractor Works",
                2024,
                "NEW",
                "Universal 81 ot kuchiga ega gʻildirakli traktor",
                "{\"power_hp\": 81, \"fuel\": \"DIESEL\", \"drive\": \"4x4\"}",
                "Toshkent viloyati",
                "Chirchiq shahri",
                "https://cdn.hosil.uz/equipment/belarus-82.jpg",
                new BigDecimal("500000.00"),
                new BigDecimal("12000000.00"),
                marketValue,
                "UZS",
                new BigDecimal("20.00"),
                12,
                36
        ));
    }

    @Test
    @DisplayName("Scenario 1: Equipment catalogue creation, public discovery, and update authorization")
    void testEquipmentCatalogueAndAuthorization() throws Exception {
        CreateEquipmentListingRequest req = new CreateEquipmentListingRequest(
                "COMBINE_HARVESTER",
                "Case IH Axial-Flow 4088 Kombayni",
                "Axial-Flow 4088",
                "Case IH",
                2025,
                "NEW",
                "Yuqori unumdor gʻalla oʻrish kombayni",
                "{\"header_width_m\": 5.1, \"engine_hp\": 210}",
                "Samarqand viloyati",
                "Jomboy tumani",
                "https://cdn.hosil.uz/equipment/case-4088.jpg",
                new BigDecimal("2500000.00"),
                new BigDecimal("45000000.00"),
                new BigDecimal("1250000000.00"),
                "UZS",
                new BigDecimal("25.00"),
                12,
                48
        );

        // 1. Create listing as supplier
        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/equipment")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.category").value("COMBINE_HARVESTER"))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"))
                .andReturn();

        EquipmentListingDto created = objectMapper.readValue(createRes.getResponse().getContentAsString(), EquipmentListingDto.class);

        // 2. Public unauthenticated discovery
        mockMvc.perform(get("/api/v1/logistics/equipment/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Case IH Axial-Flow 4088 Kombayni"))
                .andExpect(jsonPath("$.monthlyRate").value(45000000.00));

        // 3. Unauthorized stranger cannot update listing (IDOR)
        UpdateEquipmentListingRequest updateReq = new UpdateEquipmentListingRequest(
                "Ruxsatsiz oʻzgartirilgan nom",
                null, null, null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/logistics/equipment/" + created.id())
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // 4. Owner can update successfully
        UpdateEquipmentListingRequest ownerUpdate = new UpdateEquipmentListingRequest(
                "Case IH Axial-Flow 4088 (Yangilangan shartlar)",
                null, null, null, new BigDecimal("42000000.00"), null, null, null, null
        );

        mockMvc.perform(put("/api/v1/logistics/equipment/" + created.id())
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Case IH Axial-Flow 4088 (Yangilangan shartlar)"))
                .andExpect(jsonPath("$.monthlyRate").value(42000000.00));
    }

    @Test
    @DisplayName("Scenario 2: Successful leasing application submission, sandbox underwriting approval, and reservation")
    void testLeasingApplicationSubmissionAndApproval() throws Exception {
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 1)", new BigDecimal("240000000.00"));

        SubmitLeasingApplicationRequest appReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Bahodir Dehqon Xoʻjaligi",
                "+998911234567",
                24,
                new BigDecimal("60000000.00"), // 25% down payment (> 20% required)
                "Paxta va gʻalla maydonlarida agrotexnik ishlov berish uchun",
                "idemp-lease-" + UUID.randomUUID()
        );

        MvcResult res = mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.isSimulated").value(true))
                .andExpect(jsonPath("$.approvedPrincipal").value(180000000.00))
                .andExpect(jsonPath("$.approvedDownPayment").value(60000000.00))
                .andExpect(jsonPath("$.approvedTermMonths").value(24))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andReturn();

        LeasingApplicationDto dto = objectMapper.readValue(res.getResponse().getContentAsString(), LeasingApplicationDto.class);

        // Verify equipment is now RESERVED
        EquipmentListingEntity updatedTractor = equipmentRepository.findById(tractor.getId()).orElseThrow();
        assertEquals("RESERVED", updatedTractor.getAvailabilityStatus());

        // Verify status history
        var history = mockMvc.perform(get("/api/v1/logistics/leasing/applications/" + dto.id())
                        .header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Scenario 3: Application rejection when down payment is below required 20% minimum")
    void testLeasingApplicationRejectionBelowMinimumDownPayment() throws Exception {
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 2)", new BigDecimal("200000000.00"));

        SubmitLeasingApplicationRequest appReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Kichik Dehqon",
                "+998911234567",
                24,
                new BigDecimal("20000000.00"), // Only 10% (< 20% minimum)
                "Bogʻdorchilik ishlari uchun",
                "idemp-reject-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(containsString("minimal 20% dan kam")));

        // Equipment remains AVAILABLE
        EquipmentListingEntity updatedTractor = equipmentRepository.findById(tractor.getId()).orElseThrow();
        assertEquals("AVAILABLE", updatedTractor.getAvailabilityStatus());
    }

    @Test
    @DisplayName("Scenario 4: Draft saving, IDOR access protection, and applicant withdrawal")
    void testDraftSavingIdorAndWithdrawal() throws Exception {
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 3)", new BigDecimal("220000000.00"));

        SubmitLeasingApplicationRequest draftReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "DEHQON",
                "Qoralama Ariza",
                "+998911234567",
                12,
                new BigDecimal("50000000.00"),
                "Hujjatlar toʻliq tayyor emas",
                "idemp-draft-" + UUID.randomUUID()
        );

        MvcResult draftRes = mockMvc.perform(post("/api/v1/logistics/leasing/applications/draft")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        LeasingApplicationDto draftDto = objectMapper.readValue(draftRes.getResponse().getContentAsString(), LeasingApplicationDto.class);

        // IDOR: Stranger cannot view this draft application
        mockMvc.perform(get("/api/v1/logistics/leasing/applications/" + draftDto.id())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Farmer can withdraw their draft
        WithdrawApplicationRequest withdrawReq = new WithdrawApplicationRequest("Reja oʻzgardi");
        mockMvc.perform(post("/api/v1/logistics/leasing/applications/" + draftDto.id() + "/withdraw")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    @DisplayName("Scenario 5: Duplicate active application guard prevents concurrent submissions by same user")
    void testDuplicateActiveApplicationBlocked() throws Exception {
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 4)", new BigDecimal("250000000.00"));

        SubmitLeasingApplicationRequest appReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Bahodir Dehqon",
                "+998911234567",
                24,
                new BigDecimal("60000000.00"),
                "Ekin ekish mavsumi uchun",
                "idemp-dup-1"
        );

        // First submission succeeds
        mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated());

        // Second submission for the same equipment by the same applicant fails with conflict
        SubmitLeasingApplicationRequest appReq2 = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Bahodir Dehqon",
                "+998911234567",
                24,
                new BigDecimal("60000000.00"),
                "Ikkinchi takroriy ariza",
                "idemp-dup-2"
        );

        mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq2)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Scenario 6: End-to-end Contract preparation, repayment schedule generation, and signing")
    void testContractPreparationScheduleAndSigning() throws Exception {
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 5)", new BigDecimal("200000000.00"));

        SubmitLeasingApplicationRequest appReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Bahodir Dehqon Xoʻjaligi",
                "+998911234567",
                12, // 12 months term
                new BigDecimal("50000000.00"), // 50m down payment (Principal: 150m)
                "Shartnoma tuzish va toʻlov grafigini tekshirish uchun",
                "idemp-contract-" + UUID.randomUUID()
        );

        MvcResult appRes = mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();

        LeasingApplicationDto appDto = objectMapper.readValue(appRes.getResponse().getContentAsString(), LeasingApplicationDto.class);

        // 1. Prepare Contract
        MvcResult contractRes = mockMvc.perform(post("/api/v1/logistics/leasing/contracts/prepare/" + appDto.id())
                        .header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING_SIGNATURE"))
                .andExpect(jsonPath("$.approvedPrincipal").value(150000000.00))
                .andExpect(jsonPath("$.approvedDownPayment").value(50000000.00))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.isSimulated").value(true))
                .andReturn();

        LeasingContractDto contractDto = objectMapper.readValue(contractRes.getResponse().getContentAsString(), LeasingContractDto.class);

        // 2. Repayment Schedule Consistency Verification
        MvcResult scheduleRes = mockMvc.perform(get("/api/v1/logistics/leasing/contracts/" + contractDto.id() + "/repayment-schedule")
                        .header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].installmentNumber").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[11].installmentNumber").value(12))
                .andReturn();

        List<RepaymentScheduleItemDto> items = objectMapper.readValue(
                scheduleRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RepaymentScheduleItemDto.class)
        );

        assertEquals(12, items.size());
        BigDecimal totalRepaymentSum = items.stream()
                .map(RepaymentScheduleItemDto::totalInstallment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(contractDto.totalRepayment(), totalRepaymentSum);

        // 3. Sign Contract
        mockMvc.perform(post("/api/v1/logistics/leasing/contracts/" + contractDto.id() + "/sign")
                        .header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.signedAt").isNotEmpty());

        // 4. Verify equipment is now LEASED
        EquipmentListingEntity leasedTractor = equipmentRepository.findById(tractor.getId()).orElseThrow();
        assertEquals("LEASED", leasedTractor.getAvailabilityStatus());

        // 5. Subsequent attempts to lease the already-leased equipment fail
        SubmitLeasingApplicationRequest conflictingReq = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "MCHJ",
                "Boshqa Kompaniya",
                "+998939998877",
                24,
                new BigDecimal("60000000.00"),
                "Band boʻlgan texnikani olishga urinish",
                "idemp-conflict-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("mavjud emas")));
    }

    @Test
    @DisplayName("Scenario 7: Database double-allocation protection via unique index uq_equipment_active_contract")
    void testDoubleAllocationProtection() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 6 - " + testSuffix + ")", new BigDecimal("210000000.00"));

        LeasingApplicationEntity app1 = applicationRepository.save(new LeasingApplicationEntity(
                "LAP-2026-" + UUID.randomUUID().toString().substring(0, 8),
                farmerUser.getId(),
                tractor.getId(),
                null,
                "FERMER",
                "Fermer 1",
                "+998911112233",
                12,
                new BigDecimal("50000000.00"),
                new BigDecimal("210000000.00"),
                "UZS",
                "Ariza 1",
                null
        ));

        LeasingContractEntity c1 = new LeasingContractEntity(
                "LCT-2026-" + UUID.randomUUID().toString().substring(0, 8),
                app1.getId(),
                tractor.getId(),
                farmerUser.getId(),
                supplierUser.getId(),
                "MOCK_AGROBANK_SANDBOX",
                new BigDecimal("160000000.00"),
                new BigDecimal("50000000.00"),
                new BigDecimal("0.18"),
                12,
                new BigDecimal("15733333.33"),
                new BigDecimal("188800000.00"),
                "UZS",
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusMonths(12)
        );
        c1.setStatus("ACTIVE");
        contractRepository.save(c1);

        LeasingApplicationEntity app2 = applicationRepository.save(new LeasingApplicationEntity(
                "LAP-2026-" + UUID.randomUUID().toString().substring(0, 8),
                strangerUser.getId(),
                tractor.getId(),
                null,
                "FERMER",
                "Fermer 2",
                "+998933334455",
                12,
                new BigDecimal("50000000.00"),
                new BigDecimal("210000000.00"),
                "UZS",
                "Ariza 2",
                null
        ));

        // Attempting to persist a second active contract on the same equipment should violate the DB partial unique index
        LeasingContractEntity c2 = new LeasingContractEntity(
                "LCT-2026-" + UUID.randomUUID().toString().substring(0, 8),
                app2.getId(),
                tractor.getId(),
                strangerUser.getId(),
                supplierUser.getId(),
                "MOCK_AGROBANK_SANDBOX",
                new BigDecimal("160000000.00"),
                new BigDecimal("50000000.00"),
                new BigDecimal("0.18"),
                12,
                new BigDecimal("15733333.33"),
                new BigDecimal("188800000.00"),
                "UZS",
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusMonths(12)
        );
        c2.setStatus("ACTIVE");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            contractRepository.saveAndFlush(c2);
        });
    }

    @Test
    @DisplayName("Scenario 8: Repayment schedule and contract IDOR protection")
    void testContractAndScheduleIdorProtection() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 Traktori (Test 7 - " + testSuffix + ")", new BigDecimal("200000000.00"));

        LeasingApplicationEntity app = applicationRepository.save(new LeasingApplicationEntity(
                "LAP-2026-" + UUID.randomUUID().toString().substring(0, 8),
                farmerUser.getId(),
                tractor.getId(),
                null,
                "FERMER",
                "Fermer IDOR",
                "+998911112233",
                12,
                new BigDecimal("40000000.00"),
                new BigDecimal("200000000.00"),
                "UZS",
                "IDOR tekshiruvi",
                null
        ));

        LeasingContractEntity contract = new LeasingContractEntity(
                "LCT-2026-" + UUID.randomUUID().toString().substring(0, 8),
                app.getId(),
                tractor.getId(),
                farmerUser.getId(),
                supplierUser.getId(),
                "MOCK_AGROBANK_SANDBOX",
                new BigDecimal("160000000.00"),
                new BigDecimal("40000000.00"),
                new BigDecimal("0.18"),
                12,
                new BigDecimal("15733333.33"),
                new BigDecimal("188800000.00"),
                "UZS",
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusMonths(12)
        );
        LeasingContractEntity saved = contractRepository.save(contract);

        // Stranger attempts to view contract details -> HTTP 403 Forbidden
        mockMvc.perform(get("/api/v1/logistics/leasing/contracts/" + saved.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Stranger attempts to view repayment schedule -> HTTP 403 Forbidden
        mockMvc.perform(get("/api/v1/logistics/leasing/contracts/" + saved.getId() + "/repayment-schedule")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Scenario 9: Fresh session persistence and application retrieval after client session restart")
    void testSessionPersistenceAndFreshRetrieval() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        EquipmentListingEntity tractor = createSampleTractor("Belarus 82.1 (Session Test - " + testSuffix + ")", new BigDecimal("220000000.00"));

        SubmitLeasingApplicationRequest req = new SubmitLeasingApplicationRequest(
                tractor.getId(),
                null,
                "FERMER",
                "Otabek Dehqon",
                "+998901234567",
                24,
                new BigDecimal("55000000.00"),
                "Mavsumiy ekinlar uchun lizing",
                "idemp-session-" + UUID.randomUUID()
        );

        // 1. Initial submission
        MvcResult submitResult = mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();

        LeasingApplicationDto initialDto = objectMapper.readValue(submitResult.getResponse().getContentAsString(), LeasingApplicationDto.class);

        // 2. Simulate mobile app restart by generating a fresh JWT token for the same user
        String freshRestoredToken = jwtTokenProvider.generateAccessToken(farmerUser.getId(), farmerUser.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));

        // 3. Fresh independent request to load user's applications
        mockMvc.perform(get("/api/v1/logistics/leasing/applications")
                        .header("Authorization", "Bearer " + freshRestoredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(initialDto.id().toString()))
                .andExpect(jsonPath("$.content[0].applicationNumber").value(initialDto.applicationNumber()))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].isSimulated").value(true));

        // 4. Fresh independent request to load application detail by ID
        mockMvc.perform(get("/api/v1/logistics/leasing/applications/" + initialDto.id())
                        .header("Authorization", "Bearer " + freshRestoredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(initialDto.id().toString()))
                .andExpect(jsonPath("$.equipmentId").value(tractor.getId().toString()))
                .andExpect(jsonPath("$.approvedMonthlyInstallment").isNotEmpty());
    }

    @Test
    @DisplayName("Scenario 10: Unauthenticated access rejection for protected leasing endpoints")
    void testUnauthenticatedAccessRejected() throws Exception {
        // Without Authorization header -> HTTP 401 Unauthorized
        mockMvc.perform(get("/api/v1/logistics/leasing/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/logistics/leasing/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/logistics/leasing/contracts/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
