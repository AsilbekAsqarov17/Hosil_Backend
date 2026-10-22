package uz.hosil.platform.farm.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.CropDiseaseDiagnosisEntity;
import uz.hosil.platform.farm.domain.FarmActivityEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FarmMembershipEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.CropDiseaseDiagnosisRepository;
import uz.hosil.platform.farm.repository.FarmActivityRepository;
import uz.hosil.platform.farm.repository.FarmMembershipRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.service.CropHealthDiagnosticsService;
import uz.hosil.platform.farm.service.MockSandboxCropDiseaseInferenceGateway;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live PostgreSQL 16 + Flyway backed integration tests validating Milestone 8:
 * Hosil Farm AI Crop-Disease Scanner subsystem, secure image ingestion, dimension validation,
 * metadata sanitization, IDOR defenses, idempotency, retry safety, and activity conversions.
 */
@SpringBootTest
@ActiveProfiles("local")
class PostgresCropHealthDiagnosticsTests {

    @Autowired
    private CropHealthDiagnosticsService diagnosticsService;

    @Autowired
    private MockSandboxCropDiseaseInferenceGateway inferenceGateway;

    @Autowired
    private CropDiseaseDiagnosisRepository diagnosisRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private FarmMembershipRepository membershipRepository;

    @Autowired
    private FarmActivityRepository farmActivityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private static final AtomicLong PHONE_SEQ =
            new AtomicLong(System.currentTimeMillis() % 600000000L + 200000000L);

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetGateway() {
        inferenceGateway.reset();
    }

    private UserEntity createSyntheticUser(String name) {
        String phone = "+998" + PHONE_SEQ.incrementAndGet();
        UserEntity user = new UserEntity(phone, "ACTIVE", "uz");
        user = userRepository.save(user);

        UserProfileEntity profile = new UserProfileEntity(user.getId(), name, "Toshkent", "Yangiyoʻl");
        userProfileRepository.save(profile);
        return user;
    }

    private FarmEntity createSyntheticFarm(UserEntity owner, String farmName) {
        FarmEntity farm = new FarmEntity(
                owner.getId(),
                farmName,
                "Toshkent",
                "Yangiyoʻl",
                BigDecimal.valueOf(25.0),
                "FERMER"
        );
        farm = farmRepository.save(farm);

        FarmMembershipEntity membership = new FarmMembershipEntity(
                farm.getId(),
                owner.getId(),
                "OWNER",
                "WRITE_ACTIVITY,MANAGE_CROPS",
                "ACTIVE"
        );
        membershipRepository.save(membership);
        return farm;
    }

    private FieldEntity createSyntheticField(FarmEntity farm, String fieldName) {
        FieldEntity field = new FieldEntity(
                UUID.randomUUID(),
                farm.getId(),
                fieldName,
                BigDecimal.valueOf(5.0),
                "HECTARE",
                null,
                "Tomchilatib sugʻoriladigan maydon"
        );
        return fieldRepository.save(field);
    }

    private MockMultipartFile generateTestImageFile(String paramName, String filename, int width, int height, Color color) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, width, height);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] bytes = baos.toByteArray();

            return new MockMultipartFile(paramName, filename, "image/jpeg", bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate test image", e);
        }
    }

    @Test
    @DisplayName("Scenario 1: Valid leaf image submission persists diagnosis and extracts agronomic inference")
    void testValidImageSubmissionAndRetrieval() {
        UserEntity farmer = createSyntheticUser("Fermer Alisher");
        FarmEntity farm = createSyntheticFarm(farmer, "Baraka Paxta Dalasi");
        FieldEntity field = createSyntheticField(farm, "1-Maydon");

        MockMultipartFile file = generateTestImageFile("image", "tomato_leaf.jpg", 400, 400, Color.GREEN);

        CropDiseaseDiagnosisEntity diagnosis = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                farm.getId(),
                field.getId(),
                null,
                "Bargda shubhali qoramtir dogʻlar paydo boʻldi",
                null
        );

        assertNotNull(diagnosis);
        assertNotNull(diagnosis.getId());
        assertEquals("COMPLETED", diagnosis.getStatus());
        assertEquals("TOMATO_LATE_BLIGHT", diagnosis.getConditionCode());
        assertNotNull(diagnosis.getConditionName());
        assertTrue(diagnosis.getConfidenceScore().compareTo(BigDecimal.valueOf(0.50)) > 0);
        assertFalse(diagnosis.getIsHealthy());
        assertEquals("HIGH", diagnosis.getSeverity());
        assertNotNull(diagnosis.getAgronomicAdvice());
        assertNotNull(diagnosis.getCulturalControls());
        assertTrue(diagnosis.getIsSimulated());

        // Verify retrieval via getDiagnosis
        CropDiseaseDiagnosisEntity fetched = diagnosticsService.getDiagnosis(diagnosis.getId(), farmer.getId());
        assertEquals(diagnosis.getId(), fetched.getId());
        assertEquals(farmer.getId(), fetched.getUserId());

        // Verify image stream retrieval
        byte[] imageBytes = diagnosticsService.getImageBytes(diagnosis.getId(), farmer.getId());
        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0);
    }

    @Test
    @DisplayName("Scenario 2: Invalid image content and fake file formats rejected with ValidationException")
    void testInvalidImageContentRejected() {
        UserEntity farmer = createSyntheticUser("Fermer Sardor");

        // Plain text disguised as .jpg
        byte[] fakeBytes = "NOT_A_REAL_IMAGE_FILE_JUST_MALICIOUS_TEXT".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeFile = new MockMultipartFile("image", "exploit.jpg", "image/jpeg", fakeBytes);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                diagnosticsService.submitDiagnosis(
                        farmer.getId(),
                        fakeFile,
                        "TOMATO",
                        null,
                        null,
                        null,
                        "Test",
                        null
                )
        );
        assertTrue(ex.getMessage().contains("format") || ex.getMessage().contains("shikastlangan"));
    }

    @Test
    @DisplayName("Scenario 3: Undersized image dimensions rejected before inference")
    void testDimensionLimitEnforcement() {
        UserEntity farmer = createSyntheticUser("Fermer Botir");

        // 50x50 image is below 200x200 min dimension requirement
        MockMultipartFile tinyFile = generateTestImageFile("image", "tiny.jpg", 50, 50, Color.YELLOW);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                diagnosticsService.submitDiagnosis(
                        farmer.getId(),
                        tinyFile,
                        "TOMATO",
                        null,
                        null,
                        null,
                        "Test",
                        null
                )
        );
        assertTrue(ex.getMessage().contains("kichik") || ex.getMessage().contains("oʻlcham"));
    }

    @Test
    @DisplayName("Scenario 4: IDOR security protection prevents unauthorized cross-user inspection access")
    void testIdorProtectionAcrossUsers() {
        UserEntity farmer1 = createSyntheticUser("Fermer Rustam");
        UserEntity farmer2 = createSyntheticUser("Begona Foydalanuvchi");

        MockMultipartFile file = generateTestImageFile("image", "private_leaf.jpg", 300, 300, Color.GREEN);

        CropDiseaseDiagnosisEntity diagnosis = diagnosticsService.submitDiagnosis(
                farmer1.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Maxfiy ekin rasmi",
                null
        );

        // Farmer 2 attempts to view Farmer 1's private diagnosis
        assertThrows(AccessDeniedException.class, () ->
                diagnosticsService.getDiagnosis(diagnosis.getId(), farmer2.getId())
        );

        // Farmer 2 attempts to fetch Farmer 1's leaf photo
        assertThrows(AccessDeniedException.class, () ->
                diagnosticsService.getImageBytes(diagnosis.getId(), farmer2.getId())
        );
    }

    @Test
    @DisplayName("Scenario 5: Invalid farm/field association rejected with ResourceNotFoundException")
    void testInvalidFieldAndFarmAssociation() {
        UserEntity farmer = createSyntheticUser("Fermer Jamshid");
        FarmEntity farm1 = createSyntheticFarm(farmer, "Fermerlik Xoʻjaligi 1");

        UserEntity otherFarmer = createSyntheticUser("Boshqa Fermer");
        FarmEntity farm2 = createSyntheticFarm(otherFarmer, "Fermerlik Xoʻjaligi 2");
        FieldEntity fieldOnFarm2 = createSyntheticField(farm2, "Farm 2 Maydoni");

        MockMultipartFile file = generateTestImageFile("image", "leaf.jpg", 300, 300, Color.GREEN);

        // Attempting to submit on Farm 1 with Field belonging to Farm 2
        assertThrows(ResourceNotFoundException.class, () ->
                diagnosticsService.submitDiagnosis(
                        farmer.getId(),
                        file,
                        "TOMATO",
                        farm1.getId(),
                        fieldOnFarm2.getId(),
                        null,
                        "Test mismatch",
                        null
                )
        );
    }

    @Test
    @DisplayName("Scenario 6: Idempotent client retries return original diagnosis without duplicate processing")
    void testIdempotentDiagnosisRetry() {
        UserEntity farmer = createSyntheticUser("Fermer Dilshod");
        String idempotencyKey = "idemp-farm-diag-" + UUID.randomUUID();

        MockMultipartFile file = generateTestImageFile("image", "leaf.jpg", 350, 350, Color.GREEN);

        CropDiseaseDiagnosisEntity firstCall = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Dastlabki yuborish",
                idempotencyKey
        );

        // Second call with identical Idempotency-Key
        CropDiseaseDiagnosisEntity secondCall = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Qayta urinish",
                idempotencyKey
        );

        assertEquals(firstCall.getId(), secondCall.getId());
        assertEquals(firstCall.getConditionCode(), secondCall.getConditionCode());
    }

    @Test
    @DisplayName("Scenario 7: Gateway supports Healthy, Late Blight, and Unsupported/Inconclusive outcomes")
    void testMockGatewayOutcomes() {
        UserEntity farmer = createSyntheticUser("Fermer Qodir");
        MockMultipartFile file = generateTestImageFile("image", "leaf.jpg", 300, 300, Color.GREEN);

        // 7A: Healthy outcome requested via notes
        CropDiseaseDiagnosisEntity healthyDiag = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Barg sog'lom ko'rinyapti",
                null
        );
        assertEquals("COMPLETED", healthyDiag.getStatus());
        assertTrue(healthyDiag.getIsHealthy());
        assertEquals("NONE", healthyDiag.getSeverity());

        // 7B: Cotton wilt diagnosis
        CropDiseaseDiagnosisEntity cottonDiag = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "COTTON",
                null,
                null,
                null,
                "Gʻoʻza bargi qurimoqda",
                null
        );
        assertEquals("COMPLETED", cottonDiag.getStatus());
        assertEquals("COTTON_VERTICILLIUM_WILT", cottonDiag.getConditionCode());
        assertEquals("CRITICAL", cottonDiag.getSeverity());

        // 7C: Unsupported non-crop image outcome
        inferenceGateway.setSimulatedUnsupported(true);
        CropDiseaseDiagnosisEntity unsupportedDiag = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Xira rasm",
                null
        );
        assertEquals("UNSUPPORTED", unsupportedDiag.getStatus());
        assertEquals("UNSUPPORTED_IMAGE", unsupportedDiag.getConditionCode());
    }

    @Test
    @DisplayName("Scenario 8: Processing failure marks diagnosis FAILED and allows safe recovery retry")
    void testProcessingFailureAndSafeRetry() {
        UserEntity farmer = createSyntheticUser("Fermer Ilhom");
        MockMultipartFile file = generateTestImageFile("image", "leaf.jpg", 300, 300, Color.GREEN);

        // Simulate gateway timeout failure
        inferenceGateway.setSimulatedFailure(true);

        CropDiseaseDiagnosisEntity failedDiag = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "TOMATO",
                null,
                null,
                null,
                "Nosozlik sinovi",
                null
        );

        assertEquals("FAILED", failedDiag.getStatus());
        assertNotNull(failedDiag.getFailureReason());
        assertNull(failedDiag.getConditionCode());

        // Now resolve simulated issue and trigger retry
        inferenceGateway.reset();
        CropDiseaseDiagnosisEntity retriedDiag = diagnosticsService.retryDiagnosis(
                failedDiag.getId(),
                farmer.getId()
        );

        assertEquals(failedDiag.getId(), retriedDiag.getId());
        assertEquals("COMPLETED", retriedDiag.getStatus());
        assertEquals("TOMATO_LATE_BLIGHT", retriedDiag.getConditionCode());
        assertNull(retriedDiag.getFailureReason());
    }

    @Test
    @DisplayName("Scenario 9: Confirmed diagnosis converts into FarmActivityEntity with agronomic guidance notes")
    void testConvertToFarmActivity() {
        UserEntity farmer = createSyntheticUser("Fermer Otabek");
        FarmEntity farm = createSyntheticFarm(farmer, "Otabek Bogʻdorchilik");
        FieldEntity field = createSyntheticField(farm, "Olmazor Dala");

        MockMultipartFile file = generateTestImageFile("image", "apple_leaf.jpg", 300, 300, Color.GREEN);

        CropDiseaseDiagnosisEntity diagnosis = diagnosticsService.submitDiagnosis(
                farmer.getId(),
                file,
                "APPLE",
                farm.getId(),
                field.getId(),
                null,
                "Olma bargida qora dogʻlar",
                null
        );
        assertEquals("COMPLETED", diagnosis.getStatus());
        assertEquals("APPLE_SCAB", diagnosis.getConditionCode());

        // Convert to activity
        FarmActivityEntity activity = diagnosticsService.convertToActivity(
                diagnosis.getId(),
                farmer.getId(),
                "PESTICIDE",
                "Biologik fungitsid bilan ishlov berish rejalashtirildi"
        );

        assertNotNull(activity);
        assertNotNull(activity.getId());
        assertEquals(farm.getId(), activity.getFarmId());
        assertEquals(field.getId(), activity.getFieldId());
        assertEquals("PESTICIDE", activity.getActivityType());
        assertTrue(activity.getNotes().contains("AI Kasallik Tahlili Natijasi"));
        assertTrue(activity.getNotes().contains("Olma qoʻtiri"));
        assertTrue(activity.getNotes().contains("Biologik fungitsid"));
    }

    @Test
    @DisplayName("Scenario 10: PostgreSQL Flyway constraints strictly reject illegal status and confidence values")
    void testDatabaseConstraintsDirectly() {
        UserEntity farmer = createSyntheticUser("Fermer Shokir");

        // Test 10A: Illegal status violates chk_diagnosis_status
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO crop_disease_diagnoses (id, user_id, crop_type, image_storage_path, image_content_type, image_file_size, status) " +
                        "VALUES (?, ?, 'TOMATO', '/test/path.jpg', 'image/jpeg', 1024, 'ILLEGAL_STATUS')",
                        UUID.randomUUID(), farmer.getId()
                )
        );

        // Test 10B: Out-of-bounds confidence score (> 1.0) violates chk_diagnosis_confidence
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO crop_disease_diagnoses (id, user_id, crop_type, image_storage_path, image_content_type, image_file_size, status, confidence_score) " +
                        "VALUES (?, ?, 'TOMATO', '/test/path.jpg', 'image/jpeg', 1024, 'COMPLETED', 1.5000)",
                        UUID.randomUUID(), farmer.getId()
                )
        );
    }
}
