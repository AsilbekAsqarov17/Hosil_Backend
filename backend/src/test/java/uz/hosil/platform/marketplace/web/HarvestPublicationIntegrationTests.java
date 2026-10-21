package uz.hosil.platform.marketplace.web;

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
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestAllocationEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.ListingMediaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestPublicationIntegrationTests {

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
    private HarvestListingRepository listingRepository;

    @Autowired
    private HarvestAllocationRepository allocationRepository;

    @Autowired
    private ListingMediaRepository mediaRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserEntity userOwner;
    private String tokenOwner;

    private UserEntity userAgronomistNoPublish;
    private String tokenAgronomistNoPublish;

    private UserEntity userOutside;
    private String tokenOutside;

    private FarmEntity farm;
    private HarvestBatchEntity harvestBatch;

    @BeforeEach
    void setUp() {
        mediaRepository.deleteAll();
        allocationRepository.deleteAll();
        listingRepository.deleteAll();
        harvestBatchRepository.deleteAll();
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        membershipRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Seed root category if not present
        if (categoryRepository.findByCode("HARVEST").isEmpty()) {
            categoryRepository.save(new CategoryEntity(UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 1));
        }

        // 1. Owner
        userOwner = userRepository.save(new UserEntity("+998901112233", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userOwner.getId(), "Ali Fermer", "Toshkent viloyati", "Yangiyo'l"));
        tokenOwner = jwtTokenProvider.generateAccessToken(userOwner.getId(), userOwner.getPhoneNumber(), List.of("REGISTERED_USER", "FARMER"));

        // 2. Agronomist without PUBLISH_MARKET permission
        userAgronomistNoPublish = userRepository.save(new UserEntity("+998902223344", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userAgronomistNoPublish.getId(), "Vali Agronom", "Toshkent viloyati", "Yangiyo'l"));
        tokenAgronomistNoPublish = jwtTokenProvider.generateAccessToken(userAgronomistNoPublish.getId(), userAgronomistNoPublish.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 3. Outside user
        userOutside = userRepository.save(new UserEntity("+998909998877", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(userOutside.getId(), "Begona User", "Samarqand viloyati", "Tayloq"));
        tokenOutside = jwtTokenProvider.generateAccessToken(userOutside.getId(), userOutside.getPhoneNumber(), List.of("REGISTERED_USER"));

        // 4. Farm & Memberships
        farm = farmRepository.save(new FarmEntity(userOwner.getId(), "Beshqo'rg'on Agro", "Toshkent viloyati", "Yangiyo'l", new BigDecimal("30.00"), "FERMER"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userOwner.getId(), "OWNER", "READ,WRITE_ACTIVITY,RECORD_HARVEST,PUBLISH_MARKET", "ACTIVE"));
        membershipRepository.save(new FarmMembershipEntity(farm.getId(), userAgronomistNoPublish.getId(), "AGRONOMIST", "READ,WRITE_ACTIVITY,RECORD_HARVEST", "ACTIVE"));

        // 5. Field & Crop Cycle
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "1-Dala", new BigDecimal("10.00"), "HECTARE", "{\"type\": \"Polygon\"}", "Private soil data"));
        CropCycleEntity cycle = cropCycleRepository.save(new CropCycleEntity(
                UUID.randomUUID(), farm.getId(), field.getId(), "Pomidor", "Pink Paradise",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 1), "HARVESTED"
        ));

        // 6. Physical Harvest Batch: 5000.000 KG
        harvestBatch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                cycle.getId(),
                "Pomidor (Pink Paradise)",
                "FIRST_CLASS",
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"), // unallocated
                "KG",
                LocalDate.of(2026, 7, 2),
                "1-Omborxona, A-seksiya (Private warehouse note)",
                "RECORDED",
                "client-batch-init",
                null,
                1L
        ));
    }

    @Test
    void publishListing_OwnerValidPartialAllocation_Success_AndPrivacyProtection() throws Exception {
        UUID mediaPhotoId = UUID.randomUUID();
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor (Pink Paradise)",
                "FIRST_CLASS",
                "2000.000",
                "KG",
                "6500.00",
                "UZS",
                false,
                "100.000",
                "10.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                List.of(mediaPhotoId)
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sellerUserId").value(userOwner.getId().toString()))
                .andExpect(jsonPath("$.harvestBatchId").value(harvestBatch.getId().toString()))
                .andExpect(jsonPath("$.cropType").value("Pomidor (Pink Paradise)"))
                .andExpect(jsonPath("$.qualityGrade").value("FIRST_CLASS"))
                .andExpect(jsonPath("$.allocatedQuantity").value("2000.0"))
                .andExpect(jsonPath("$.availableQuantity").value("2000.0"))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.soldQuantity").value(0))
                .andExpect(jsonPath("$.unitCode").value("KG"))
                .andExpect(jsonPath("$.unitPrice").value("6500.0"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.mediaFileIds", hasSize(1)))
                .andExpect(jsonPath("$.mediaFileIds[0]").value(mediaPhotoId.toString()))
                // PRIVACY VERIFICATION: Private farm data is completely excluded
                .andExpect(jsonPath("$.storageLocationNotes").doesNotExist())
                .andExpect(jsonPath("$.storageNotes").doesNotExist())
                .andExpect(jsonPath("$.boundaryGeoJson").doesNotExist())
                .andExpect(jsonPath("$.privateNotes").doesNotExist());

        // Verify Database State:
        // 1. Backing harvest batch unallocated quantity decremented
        HarvestBatchEntity updatedBatch = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("3000.000"), updatedBatch.getUnallocatedQuantity());
        assertEquals("PARTIALLY_ALLOCATED", updatedBatch.getStatus());

        // 2. Harvest Allocation ledger entry created
        List<HarvestAllocationEntity> allocations = allocationRepository.findByHarvestBatchId(harvestBatch.getId());
        assertEquals(1, allocations.size());
        assertEquals(new BigDecimal("2000.000"), allocations.get(0).getAllocatedQuantity());
        assertEquals("ACTIVE", allocations.get(0).getStatus());

        // 3. Harvest Listing created
        List<HarvestListingEntity> listings = listingRepository.findByHarvestBatchId(harvestBatch.getId());
        assertEquals(1, listings.size());
        assertEquals(new BigDecimal("2000.000"), listings.get(0).getAllocatedQuantity());
        assertEquals(new BigDecimal("2000.000"), listings.get(0).getAvailableQuantity());

        // 4. Quantity Conservation Check:
        // total_quantity == unallocated_quantity + sum(active_allocations)
        BigDecimal totalBatch = updatedBatch.getRecordedQuantity();
        BigDecimal unallocated = updatedBatch.getUnallocatedQuantity();
        BigDecimal allocatedTotal = allocations.stream().map(HarvestAllocationEntity::getAllocatedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalBatch.compareTo(unallocated.add(allocatedTotal)));
    }

    @Test
    void publishListing_NonOwnerWithoutPublishPermission_Returns403() throws Exception {
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "1000.000",
                "KG",
                "5000.00",
                "UZS",
                false,
                "50.000",
                "5.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenAgronomistNoPublish)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishListing_OutsideUser_Returns403() throws Exception {
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "1000.000",
                "KG",
                "5000.00",
                "UZS",
                false,
                "50.000",
                "5.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOutside)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishListing_ExceedingUnallocatedQuantity_Returns409Conflict_AndAtomicRollback() throws Exception {
        // Attempting to allocate 6000.000 KG from 5000.000 KG batch
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "6000.000",
                "KG",
                "5000.00",
                "UZS",
                false,
                "100.000",
                "10.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("INSUFFICIENT_UNALLOCATED_QUANTITY")));

        // ATOMICITY VERIFICATION: Batch unallocated quantity remains completely untouched
        HarvestBatchEntity batchAfterFailure = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("5000.000"), batchAfterFailure.getUnallocatedQuantity());
        assertEquals("RECORDED", batchAfterFailure.getStatus());

        // Zero listings and zero allocations were persisted
        assertEquals(0, listingRepository.findByHarvestBatchId(harvestBatch.getId()).size());
        assertEquals(0, allocationRepository.findByHarvestBatchId(harvestBatch.getId()).size());
    }

    @Test
    void publishListing_UnitMismatch_Returns409Conflict() throws Exception {
        // Batch unit is KG, request specifies TON
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "2.000",
                "TON",
                "5000000.00",
                "UZS",
                false,
                "1.000",
                "1.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Unit code mismatch")));
    }

    @Test
    void publishListing_MoqExceedsAllocated_Returns409Conflict() throws Exception {
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "100.000",
                "KG",
                "6000.00",
                "UZS",
                false,
                "200.000", // MOQ > allocated
                "10.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void publishListing_IdempotentReplay_ReturnsExistingListing() throws Exception {
        CreateHarvestListingRequest request = new CreateHarvestListingRequest(
                harvestBatch.getId(),
                "Pomidor",
                "FIRST_CLASS",
                "1000.000",
                "KG",
                "6000.00",
                "UZS",
                false,
                "50.000",
                "10.000",
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                null
        );

        String idempotencyKey = "publish-key-idemp-001";

        // 1. Initial publish -> 201 Created
        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedQuantity").value("1000.0"));

        // 2. Retry with identical Idempotency-Key -> 200 OK with IDEMPOTENT_HIT
        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andExpect(jsonPath("$.allocatedQuantity").value("1000.0"));

        // Verify only 1 listing and 1 allocation exist, and exactly 1000.000 KG was allocated
        assertEquals(1, listingRepository.findByHarvestBatchId(harvestBatch.getId()).size());
        assertEquals(1, allocationRepository.findByHarvestBatchId(harvestBatch.getId()).size());
        HarvestBatchEntity batch = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("4000.000"), batch.getUnallocatedQuantity());
    }

    @Test
    void publishListing_MultiplePartialAllocations_QuantityConservation() throws Exception {
        // Allocation 1: 2000.000 KG
        CreateHarvestListingRequest req1 = new CreateHarvestListingRequest(
                harvestBatch.getId(), "Pomidor 1", "FIRST_CLASS", "2000.000", "KG",
                "6000.00", "UZS", false, "50.000", "10.000", "Toshkent", "Yangiyoʻl", null
        );
        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        HarvestBatchEntity batchAfter1 = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("3000.000"), batchAfter1.getUnallocatedQuantity());
        assertEquals("PARTIALLY_ALLOCATED", batchAfter1.getStatus());

        // Allocation 2: 3000.000 KG (Remaining fully depleted)
        CreateHarvestListingRequest req2 = new CreateHarvestListingRequest(
                harvestBatch.getId(), "Pomidor 2", "SECOND_CLASS", "3000.000", "KG",
                "5000.00", "UZS", false, "50.000", "10.000", "Toshkent", "Yangiyoʻl", null
        );
        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        HarvestBatchEntity batchAfter2 = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.000"), batchAfter2.getUnallocatedQuantity());
        assertEquals("FULLY_ALLOCATED", batchAfter2.getStatus());

        // Allocation 3: Attempting 1.000 KG -> must fail because batch is depleted
        CreateHarvestListingRequest req3 = new CreateHarvestListingRequest(
                harvestBatch.getId(), "Pomidor 3", "FIRST_CLASS", "1.000", "KG",
                "6000.00", "UZS", false, "1.000", "1.000", "Toshkent", "Yangiyoʻl", null
        );
        mockMvc.perform(post("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isConflict());

        // Strict Quantity Conservation Equation Verification:
        // recorded_quantity (5000.000) == unallocated_quantity (0.000) + sum(allocations) (2000.000 + 3000.000)
        List<HarvestAllocationEntity> allAllocations = allocationRepository.findByHarvestBatchId(harvestBatch.getId());
        assertEquals(2, allAllocations.size());
        BigDecimal totalAllocated = allAllocations.stream()
                .map(HarvestAllocationEntity::getAllocatedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("5000.000"), totalAllocated);
        assertEquals(0, batchAfter2.getRecordedQuantity().compareTo(batchAfter2.getUnallocatedQuantity().add(totalAllocated)));
    }

    @Test
    void publishListing_ConcurrentAllocations_ThreadSafetyAndNoOverselling() throws Exception {
        // Batch has 5000.000 KG.
        // We launch 5 concurrent threads, each attempting to allocate 2000.000 KG.
        // Exactly 2 threads can succeed (2 * 2000 = 4000 <= 5000).
        // 3 threads must be rejected with 409 Conflict.
        // Remaining unallocated quantity must be strictly 1000.000 KG.
        int totalThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // synchronizes start of all threads
                    CreateHarvestListingRequest req = new CreateHarvestListingRequest(
                            harvestBatch.getId(),
                            "Pomidor Concurrent " + index,
                            "FIRST_CLASS",
                            "2000.000",
                            "KG",
                            "6000.00",
                            "UZS",
                            false,
                            "50.000",
                            "10.000",
                            "Toshkent",
                            "Yangiyoʻl",
                            null
                    );

                    mockMvc.perform(post("/api/v1/market/harvest-listings")
                                    .header("Authorization", "Bearer " + tokenOwner)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andDo(result -> {
                                int status = result.getResponse().getStatus();
                                if (status == 201) {
                                    successCount.incrementAndGet();
                                } else if (status == 409) {
                                    conflictCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown(); // trigger all threads concurrently
        finishGate.await();
        executor.shutdown();

        assertEquals(2, successCount.get(), "Exactly 2 concurrent requests should have succeeded");
        assertEquals(3, conflictCount.get(), "Exactly 3 concurrent requests should have been rejected with 409 Conflict");

        HarvestBatchEntity finalBatch = harvestBatchRepository.findById(harvestBatch.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.000"), finalBatch.getUnallocatedQuantity());

        List<HarvestAllocationEntity> allocations = allocationRepository.findByHarvestBatchId(harvestBatch.getId());
        assertEquals(2, allocations.size());

        BigDecimal sumAllocated = allocations.stream()
                .map(HarvestAllocationEntity::getAllocatedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("4000.000"), sumAllocated);

        // Quantity conservation check
        assertEquals(0, finalBatch.getRecordedQuantity().compareTo(finalBatch.getUnallocatedQuantity().add(sumAllocated)));
    }
}
