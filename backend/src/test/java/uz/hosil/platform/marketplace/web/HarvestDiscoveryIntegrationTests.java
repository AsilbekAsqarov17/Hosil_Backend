package uz.hosil.platform.marketplace.web;

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
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.ListingMediaEntity;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.ListingMediaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestDiscoveryIntegrationTests {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private HarvestListingRepository listingRepository;

    @Autowired
    private ListingMediaRepository mediaRepository;

    @Autowired
    private HarvestAllocationRepository allocationRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FarmMembershipRepository membershipRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    private UserEntity sellerUser;
    private UserEntity buyerUser;
    private String buyerToken;
    private CategoryEntity harvestCategory;
    private HarvestBatchEntity harvestBatch;

    private HarvestListingEntity listingTomatoPublished;
    private HarvestListingEntity listingCarrotPublished;
    private HarvestListingEntity listingAppleRfqPublished;
    private HarvestListingEntity listingDraft;
    private HarvestListingEntity listingPaused;
    private HarvestListingEntity listingArchived;
    private HarvestListingEntity listingSuspended;

    private UUID mediaId1;
    private UUID mediaId2;

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

        // 1. Ensure Root Category exists
        harvestCategory = categoryRepository.findByCode("HARVEST")
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(
                        UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 1
                )));

        // 2. Users
        sellerUser = userRepository.save(new UserEntity("+998901234567", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(sellerUser.getId(), "Bahrom Dehqon", "Toshkent viloyati", "Yangiyoʻl"));

        buyerUser = userRepository.save(new UserEntity("+998909876543", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyerUser.getId(), "Anvar Xaridor", "Toshkent shahri", "Chilonzor"));
        buyerToken = jwtTokenProvider.generateAccessToken(buyerUser.getId(), buyerUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        // 3. Farm & Harvest Batch (with private details to verify non-leakage)
        FarmEntity farm = farmRepository.save(new FarmEntity(sellerUser.getId(), "Yashil Voha Farm", "Toshkent viloyati", "Yangiyoʻl", new BigDecimal("50.00"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "7-Dala Maxfiy", new BigDecimal("12.00"), "HECTARE", "{\"type\":\"Polygon\",\"coordinates\":[[[69.1,41.2],[69.2,41.2],[69.2,41.3],[69.1,41.3],[69.1,41.2]]]}", "Private agrochemicals applied: Glyphosate 5L"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Pomidor", "Pink Paradise F1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1), "HARVESTED"));

        harvestBatch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                cropCycle.getId(),
                "Pomidor (Pink Paradise)",
                "FIRST_CLASS",
                new BigDecimal("10000.000"),
                new BigDecimal("5000.000"),
                "KG",
                LocalDate.of(2026, 7, 2),
                "Maxfiy omborxona 4-boʻlinma, kalit qorovulda (Private internal warehouse note)",
                "RECORDED",
                "client-batch-discovery-test",
                null,
                1L
        ));

        // 4. Seed Harvest Listings with diverse attributes
        Instant now = Instant.now();

        // Listing 1: Pomidor (Toshkent, FIRST_CLASS, Fixed 6500.00 UZS, PUBLISHED)
        listingTomatoPublished = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Pomidor Pink Paradise",
                "Aʼlo sifatli issiqxona pomidori",
                "Pomidor (Pink Paradise)",
                "FIRST_CLASS",
                new BigDecimal("6500.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("2000.000"),
                new BigDecimal("2000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Toshkent viloyati",
                "Yangiyoʻl tumani",
                "PUBLISHED",
                null,
                now.minusSeconds(3600)
        ));

        // Attach Media to Listing 1
        mediaId1 = UUID.randomUUID();
        mediaId2 = UUID.randomUUID();
        mediaRepository.save(new ListingMediaEntity(UUID.randomUUID(), listingTomatoPublished.getId(), mediaId1, 1));
        mediaRepository.save(new ListingMediaEntity(UUID.randomUUID(), listingTomatoPublished.getId(), mediaId2, 2));

        // Listing 2: Sabzi (Samarqand, STANDARD, Fixed 3500.00 UZS, PUBLISHED)
        listingCarrotPublished = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Kuzgi qizil sabzi",
                "Shirin sharbatli sabzi",
                "Sabzi (Kuzgi)",
                "STANDARD",
                new BigDecimal("3500.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("500.000"),
                new BigDecimal("50.000"),
                "Samarqand viloyati",
                "Jomboy tumani",
                "PUBLISHED",
                null,
                now.minusSeconds(1800)
        ));

        // Listing 3: Olma (Namangan, PREMIUM, RFQ enabled, unitPrice is null, PUBLISHED)
        listingAppleRfqPublished = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Golden Delicious Olma",
                "Eksportbop olma navi",
                "Olma (Golden Delicious)",
                "PREMIUM",
                null,
                "UZS",
                "RFQ",
                true,
                new BigDecimal("3000.000"),
                new BigDecimal("3000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("200.000"),
                new BigDecimal("20.000"),
                "Namangan viloyati",
                "Chust tumani",
                "PUBLISHED",
                null,
                now.minusSeconds(600)
        ));

        // Listing 4: DRAFT (Should NOT appear in discovery)
        listingDraft = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Qoragʻat (Draft)",
                "Draft description",
                "Qoragʻat",
                "FIRST_CLASS",
                new BigDecimal("12000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("500.000"),
                new BigDecimal("500.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("50.000"),
                new BigDecimal("5.000"),
                "Toshkent viloyati",
                "Boʻstonliq tumani",
                "DRAFT",
                null,
                now
        ));

        // Listing 5: PAUSED (Should NOT appear in discovery)
        listingPaused = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Uzum (Paused)",
                "Paused description",
                "Uzum (Husiyni)",
                "PREMIUM",
                new BigDecimal("9000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("1500.000"),
                new BigDecimal("1500.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Fargʻona viloyati",
                "Oltiariq tumani",
                "PAUSED",
                null,
                now
        ));

        // Listing 6: ARCHIVED (Should NOT appear in discovery)
        listingArchived = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Piyoz (Archived)",
                "Archived description",
                "Piyoz",
                "STANDARD",
                new BigDecimal("2000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("1000.000"),
                new BigDecimal("1000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Surxondaryo viloyati",
                "Denov tumani",
                "ARCHIVED",
                null,
                now
        ));

        // Listing 7: SUSPENDED (Should NOT appear in discovery)
        listingSuspended = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                harvestBatch.getId(),
                harvestCategory.getId(),
                "Bodring (Suspended)",
                "Suspended description",
                "Bodring",
                "FIRST_CLASS",
                new BigDecimal("4000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("1000.000"),
                new BigDecimal("1000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Toshkent viloyati",
                "Zangiota tumani",
                "SUSPENDED",
                null,
                now
        ));
    }

    // =========================================================================
    // 1. PUBLIC DISCOVERY & STATUS FILTERING
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated guest buyer can discover published listings")
    void searchListings_UnauthenticatedGuest_ReturnsPublishedListings() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("PUBLISHED"))));
    }

    @Test
    @DisplayName("Authenticated buyer can discover published listings")
    void searchListings_AuthenticatedBuyer_ReturnsPublishedListings() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .header("Authorization", "Bearer " + buyerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    @DisplayName("Non-published listings (DRAFT, PAUSED, ARCHIVED, SUSPENDED) are strictly excluded from search")
    void searchListings_ExcludesNonPublishedListings() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItems(
                        listingDraft.getId().toString(),
                        listingPaused.getId().toString(),
                        listingArchived.getId().toString(),
                        listingSuspended.getId().toString()
                ))));
    }

    // =========================================================================
    // 2. MULTI-FACET FILTERING
    // =========================================================================

    @Test
    @DisplayName("Filter by cropType (case-insensitive substring match)")
    void searchListings_FilterByCropType() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("cropType", "pomidor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(listingTomatoPublished.getId().toString())))
                .andExpect(jsonPath("$.content[0].cropType", is("Pomidor (Pink Paradise)")));
    }

    @Test
    @DisplayName("Filter by region and district")
    void searchListings_FilterByRegionAndDistrict() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("region", "Toshkent")
                        .param("district", "Yangiyoʻl")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(listingTomatoPublished.getId().toString())))
                .andExpect(jsonPath("$.content[0].region", is("Toshkent viloyati")))
                .andExpect(jsonPath("$.content[0].district", is("Yangiyoʻl tumani")));
    }

    @Test
    @DisplayName("Filter by qualityGrade")
    void searchListings_FilterByQualityGrade() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("qualityGrade", "FIRST_CLASS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(listingTomatoPublished.getId().toString())))
                .andExpect(jsonPath("$.content[0].qualityGrade", is("FIRST_CLASS")));
    }

    @Test
    @DisplayName("Filter by price range (minPrice and maxPrice)")
    void searchListings_FilterByPriceRange() throws Exception {
        // Between 3000 and 4000 UZS -> only Sabzi (3500 UZS) matches
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("minPrice", "3000.00")
                        .param("maxPrice", "4000.00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(listingCarrotPublished.getId().toString())))
                .andExpect(jsonPath("$.content[0].unitPrice", is(3500.0)));
    }

    @Test
    @DisplayName("Filter by isRfqEnabled=true returns only RFQ listings")
    void searchListings_FilterByIsRfqEnabled_True() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("isRfqEnabled", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(listingAppleRfqPublished.getId().toString())))
                .andExpect(jsonPath("$.content[0].isRfqEnabled", is(true)))
                .andExpect(jsonPath("$.content[0].unitPrice", nullValue()));
    }

    @Test
    @DisplayName("Filter by isRfqEnabled=false returns only fixed-price listings")
    void searchListings_FilterByIsRfqEnabled_False() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("isRfqEnabled", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].isRfqEnabled", everyItem(is(false))))
                .andExpect(jsonPath("$.content[*].unitPrice", not(hasItem(nullValue()))));
    }

    // =========================================================================
    // 3. VALIDATION RULES (400 BAD REQUEST)
    // =========================================================================

    @Test
    @DisplayName("Negative minPrice returns 400 Bad Request")
    void searchListings_NegativeMinPrice_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("minPrice", "-500.00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FILTER_CRITERIA")));
    }

    @Test
    @DisplayName("Negative maxPrice returns 400 Bad Request")
    void searchListings_NegativeMaxPrice_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("maxPrice", "-100.00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FILTER_CRITERIA")));
    }

    @Test
    @DisplayName("minPrice greater than maxPrice returns 400 Bad Request")
    void searchListings_MinPriceGreaterThanMaxPrice_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("minPrice", "10000.00")
                        .param("maxPrice", "5000.00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FILTER_CRITERIA")));
    }

    @Test
    @DisplayName("Invalid qualityGrade returns 400 Bad Request")
    void searchListings_InvalidQualityGrade_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("qualityGrade", "SUPER_EXTRA_UNKNOWN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FILTER_CRITERIA")));
    }

    // =========================================================================
    // 4. SORTING & PAGINATION WITH STABLE TIE-BREAKER
    // =========================================================================

    @Test
    @DisplayName("Sorting by unitPrice asc orders Sabzi (3500) before Tomato (6500)")
    void searchListings_SortByUnitPriceAsc() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("isRfqEnabled", "false")
                        .param("sort", "unitPrice,asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(listingCarrotPublished.getId().toString())))
                .andExpect(jsonPath("$.content[1].id", is(listingTomatoPublished.getId().toString())));
    }

    @Test
    @DisplayName("Sorting by unitPrice desc orders Tomato (6500) before Sabzi (3500)")
    void searchListings_SortByUnitPriceDesc() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("isRfqEnabled", "false")
                        .param("sort", "unitPrice,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(listingTomatoPublished.getId().toString())))
                .andExpect(jsonPath("$.content[1].id", is(listingCarrotPublished.getId().toString())));
    }

    @Test
    @DisplayName("Pagination parameters (page, size) properly limit result sets and include PageEnvelope metadata")
    void searchListings_Pagination_ReturnsCorrectEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageSize", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.isLast", is(false)));
    }

    // =========================================================================
    // 5. DETAIL VIEW & PRIVACY BOUNDARY
    // =========================================================================

    @Test
    @DisplayName("Get listing detail returns 200 OK with mediaFileIds for PUBLISHED listing")
    void getListingDetail_PublishedListing_Success() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingTomatoPublished.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(listingTomatoPublished.getId().toString())))
                .andExpect(jsonPath("$.cropType", is("Pomidor (Pink Paradise)")))
                .andExpect(jsonPath("$.unitPrice", is(6500.0)))
                .andExpect(jsonPath("$.currency", is("UZS")))
                .andExpect(jsonPath("$.isRfqEnabled", is(false)))
                .andExpect(jsonPath("$.region", is("Toshkent viloyati")))
                .andExpect(jsonPath("$.district", is("Yangiyoʻl tumani")))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.mediaFileIds", hasSize(2)))
                .andExpect(jsonPath("$.mediaFileIds", hasItems(mediaId1.toString(), mediaId2.toString())));
    }

    @Test
    @DisplayName("Get listing detail for non-published listing returns 404 Not Found (no leakage of draft/paused items)")
    void getListingDetail_NonPublishedListing_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingDraft.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingPaused.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingArchived.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingSuspended.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Get listing detail for non-existent listing returns 404 Not Found")
    void getListingDetail_NonExistentListing_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Data privacy verification: public responses strictly exclude private farm attributes")
    void privacyVerification_ExcludesPrivateFarmData() throws Exception {
        mockMvc.perform(get("/api/v1/market/harvest-listings/" + listingTomatoPublished.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Assert public commercial fields ARE present
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cropType").exists())
                .andExpect(jsonPath("$.availableQuantity").exists())
                .andExpect(jsonPath("$.region").exists())
                // Assert private agronomic and warehouse data ARE NOT leaked
                .andExpect(jsonPath("$.farmId").doesNotExist())
                .andExpect(jsonPath("$.fieldId").doesNotExist())
                .andExpect(jsonPath("$.cropCycleId").doesNotExist())
                .andExpect(jsonPath("$.geoJson").doesNotExist())
                .andExpect(jsonPath("$.coordinates").doesNotExist())
                .andExpect(jsonPath("$.storageLocationNotes").doesNotExist())
                .andExpect(jsonPath("$.storageNotes").doesNotExist())
                .andExpect(jsonPath("$.chemicalTreatments").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }
}
