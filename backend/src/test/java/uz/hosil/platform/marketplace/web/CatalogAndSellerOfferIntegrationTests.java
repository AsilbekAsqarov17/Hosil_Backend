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
import org.springframework.test.web.servlet.MvcResult;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryAllowedUnitEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryAllowedUnitRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAndSellerOfferIntegrationTests {

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
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryAllowedUnitRepository categoryAllowedUnitRepository;

    @Autowired
    private CatalogProductRepository catalogProductRepository;

    @Autowired
    private SellerOfferRepository sellerOfferRepository;

    private UserEntity verifiedSupplierA;
    private UserEntity verifiedSupplierB;
    private UserEntity unverifiedSupplier;
    private UserEntity regularBuyer;
    private UserEntity adminUser;

    private String supplierTokenA;
    private String supplierTokenB;
    private String unverifiedSupplierToken;
    private String buyerToken;
    private String adminToken;

    private CategoryEntity fertilizerCategory;
    private CategoryEntity machineryCategory;
    private CatalogProductEntity selitraProduct;

    @BeforeEach
    void setUp() {
        sellerOfferRepository.deleteAll();
        catalogProductRepository.deleteAll();
        categoryAllowedUnitRepository.deleteAll();
        categoryRepository.deleteAll();
        sellerProfileRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Categories
        fertilizerCategory = categoryRepository.save(new CategoryEntity(
                UUID.randomUUID(), null, "FERTILIZER", "Oʻgʻitlar va kimyoviy preparatlar", "Удобрения и агрохимия", 1, "AGRICULTURAL_INPUT", true
        ));

        machineryCategory = categoryRepository.save(new CategoryEntity(
                UUID.randomUUID(), null, "MACHINERY", "Qishloq xoʻjaligi texnikasi", "Сельхозтехника", 2, "AGRICULTURAL_INPUT", true
        ));

        // 2. Allowed Units per Category
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(fertilizerCategory.getId(), "KG"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(fertilizerCategory.getId(), "TON"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(fertilizerCategory.getId(), "BAG_50KG"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(fertilizerCategory.getId(), "LITER"));

        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(machineryCategory.getId(), "PIECE"));

        // 3. Master Catalog Product
        selitraProduct = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(),
                fertilizerCategory.getId(),
                "Ammiakli selitra (N 34.4%)",
                "Аммиачная селитра (N 34.4%)",
                "MAXAM-CHIRCHIQ",
                "Azot (N) 34.4%",
                "BAG_50KG",
                "Yuqori sifatli mineral oʻgʻit",
                true
        ));

        // 4. Users & Roles
        // Verified Supplier A
        verifiedSupplierA = userRepository.save(new UserEntity("+998901111111", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(verifiedSupplierA.getId(), "Akmal Taʼminotchi", "Toshkent viloyati", "Chirchiq"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), "COMMERCIAL_SUPPLIER", "Chirchiq Agro Kimyo MCHJ", "123456789", "VERIFIED", Instant.now()
        ));
        supplierTokenA = jwtTokenProvider.generateAccessToken(verifiedSupplierA.getId(), verifiedSupplierA.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        // Verified Supplier B
        verifiedSupplierB = userRepository.save(new UserEntity("+998902222222", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(verifiedSupplierB.getId(), "Bekzod Savdo", "Samarqand viloyati", "Jomboy"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), verifiedSupplierB.getId(), "COMMERCIAL_SUPPLIER", "Samarqand Agro Oʻgʻit XK", "987654321", "VERIFIED", Instant.now()
        ));
        supplierTokenB = jwtTokenProvider.generateAccessToken(verifiedSupplierB.getId(), verifiedSupplierB.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        // Unverified Supplier
        unverifiedSupplier = userRepository.save(new UserEntity("+998903333333", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(unverifiedSupplier.getId(), "Umar Tasdiqsiz", "Fargʻona viloyati", "Quva"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), unverifiedSupplier.getId(), "COMMERCIAL_SUPPLIER", "Yangi Agro Invest", "555555555", "UNVERIFIED", null
        ));
        unverifiedSupplierToken = jwtTokenProvider.generateAccessToken(unverifiedSupplier.getId(), unverifiedSupplier.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        // Regular Buyer
        regularBuyer = userRepository.save(new UserEntity("+998904444444", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(regularBuyer.getId(), "Nodir Xaridor", "Toshkent shahri", "Chilonzor"));
        buyerToken = jwtTokenProvider.generateAccessToken(regularBuyer.getId(), regularBuyer.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        // Admin User
        adminUser = userRepository.save(new UserEntity("+998900000001", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(adminUser.getId(), "Super Admin", "Toshkent shahri", "Yunusobod"));
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getPhoneNumber(), List.of("REGISTERED_USER", "ADMINISTRATOR"));
    }

    @Test
    @DisplayName("Public Catalog Discovery: list categories and filter by categoryType")
    void shouldListCategoriesAndFilterByType() throws Exception {
        mockMvc.perform(get("/api/v1/market/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("FERTILIZER"))
                .andExpect(jsonPath("$[1].code").value("MACHINERY"));

        mockMvc.perform(get("/api/v1/market/catalog/categories?categoryType=AGRICULTURAL_INPUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Public Catalog Discovery: list products with pagination and search")
    void shouldListProductsWithPaginationAndSearch() throws Exception {
        mockMvc.perform(get("/api/v1/market/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nameUz").value("Ammiakli selitra (N 34.4%)"))
                .andExpect(jsonPath("$.content[0].brandName").value("MAXAM-CHIRCHIQ"));

        // Search match
        mockMvc.perform(get("/api/v1/market/catalog/products?search=selitra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Search no match
        mockMvc.perform(get("/api/v1/market/catalog/products?search=traktor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Public Catalog Discovery: get product detail with price range and active offers count")
    void shouldGetProductDetail() throws Exception {
        // Seed 2 active offers for the product
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("180000.00"), "UZS", "BAG_50KG",
                new BigDecimal("500.000"), BigDecimal.ZERO, new BigDecimal("10.000"), BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", null, "ACTIVE"
        ));
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierB.getId(), selitraProduct.getId(),
                new BigDecimal("175000.00"), "UZS", "BAG_50KG",
                new BigDecimal("300.000"), BigDecimal.ZERO, new BigDecimal("5.000"), BigDecimal.ONE,
                "Samarqand viloyati", "Jomboy", null, "ACTIVE"
        ));

        mockMvc.perform(get("/api/v1/market/catalog/products/{id}", selitraProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(selitraProduct.getId().toString()))
                .andExpect(jsonPath("$.nameUz").value("Ammiakli selitra (N 34.4%)"))
                .andExpect(jsonPath("$.activeOffersCount").value(2))
                .andExpect(jsonPath("$.minPrice").value(175000.00))
                .andExpect(jsonPath("$.maxPrice").value(180000.00));
    }

    @Test
    @DisplayName("Public Catalog Discovery: list offers for a product with filters and exclude unpurchasable offers")
    void shouldListOffersForProductAndFilterCorrectly() throws Exception {
        // Active offer from Supplier A in Toshkent
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("180000.00"), "UZS", "BAG_50KG",
                new BigDecimal("500.000"), BigDecimal.ZERO, new BigDecimal("10.000"), BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", "Tez yetkazib berish", "ACTIVE"
        ));
        // Active offer from Supplier B in Samarqand
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierB.getId(), selitraProduct.getId(),
                new BigDecimal("175000.00"), "UZS", "BAG_50KG",
                new BigDecimal("300.000"), BigDecimal.ZERO, new BigDecimal("5.000"), BigDecimal.ONE,
                "Samarqand viloyati", "Jomboy", null, "ACTIVE"
        ));
        // Inactive / zero stock offers that MUST NOT appear publicly
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("160000.00"), "UZS", "BAG_50KG",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", "Tugagan", "ACTIVE"
        ));
        sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("150000.00"), "UZS", "BAG_50KG",
                new BigDecimal("100.000"), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", "Muzlatilgan", "PAUSED"
        ));

        // Public query: sees only the 2 active in-stock offers
        mockMvc.perform(get("/api/v1/market/catalog/products/{id}/offers", selitraProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].sellerBusinessName").isNotEmpty());

        // Filter by region: Samarqand
        mockMvc.perform(get("/api/v1/market/catalog/products/{id}/offers?region=Samarqand viloyati", selitraProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unitPrice").value(175000.00))
                .andExpect(jsonPath("$.content[0].sellerBusinessName").value("Samarqand Agro Oʻgʻit XK"));

        // Filter by price range
        mockMvc.perform(get("/api/v1/market/catalog/products/{id}/offers?minPrice=178000.00", selitraProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unitPrice").value(180000.00));
    }

    @Test
    @DisplayName("Seller Offer Management: verified supplier creates offer successfully")
    void shouldAllowVerifiedSupplierToCreateOffer() throws Exception {
        CreateSellerOfferRequest request = new CreateSellerOfferRequest(
                selitraProduct.getId(),
                new BigDecimal("182000.00"),
                "UZS",
                "BAG_50KG",
                new BigDecimal("1200.000"),
                new BigDecimal("20.000"),
                new BigDecimal("5.000"),
                "Toshkent viloyati",
                "Chirchiq",
                "Katta hajmda chegirma bor"
        );

        mockMvc.perform(post("/api/v1/market/seller/offers")
                        .header("Authorization", "Bearer " + supplierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sellerUserId").value(verifiedSupplierA.getId().toString()))
                .andExpect(jsonPath("$.catalogProductId").value(selitraProduct.getId().toString()))
                .andExpect(jsonPath("$.unitPrice").value(182000.00))
                .andExpect(jsonPath("$.availableQuantity").value(1200.0))
                .andExpect(jsonPath("$.minOrderQuantity").value(20.0))
                .andExpect(jsonPath("$.stepQuantity").value(5.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertEquals(1, sellerOfferRepository.count());
    }

    @Test
    @DisplayName("Seller Offer Management: unverified supplier is rejected with 403 Forbidden (DEC-004)")
    void shouldRejectUnverifiedSupplierFromCreatingOffer() throws Exception {
        CreateSellerOfferRequest request = new CreateSellerOfferRequest(
                selitraProduct.getId(),
                new BigDecimal("180000.00"),
                "UZS",
                "BAG_50KG",
                new BigDecimal("100.000"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                "Fargʻona",
                "Quva",
                null
        );

        mockMvc.perform(post("/api/v1/market/seller/offers")
                        .header("Authorization", "Bearer " + unverifiedSupplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        assertEquals(0, sellerOfferRepository.count());
    }

    @Test
    @DisplayName("Seller Offer Management: incompatible unit is rejected with 409 Conflict (DEC-009)")
    void shouldRejectOfferWithIncompatibleUnit() throws Exception {
        // PIECE is not allowed for FERTILIZER category
        CreateSellerOfferRequest request = new CreateSellerOfferRequest(
                selitraProduct.getId(),
                new BigDecimal("180000.00"),
                "UZS",
                "PIECE",
                new BigDecimal("100.000"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                "Toshkent",
                "Chirchiq",
                null
        );

        mockMvc.perform(post("/api/v1/market/seller/offers")
                        .header("Authorization", "Bearer " + supplierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Seller Offer Management: supplier lists and updates their own offers")
    void shouldAllowSupplierToManageTheirOwnOffers() throws Exception {
        // Create offer
        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("185000.00"), "UZS", "BAG_50KG",
                new BigDecimal("500.000"), BigDecimal.ZERO, new BigDecimal("10.000"), BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", "Dastlabki izoh", "ACTIVE"
        ));

        // Supplier A gets their offers
        mockMvc.perform(get("/api/v1/market/seller/offers")
                        .header("Authorization", "Bearer " + supplierTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(offer.getId().toString()));

        // Supplier A updates their offer
        UpdateSellerOfferRequest updateReq = new UpdateSellerOfferRequest(
                new BigDecimal("179000.00"), "UZS", "BAG_50KG",
                new BigDecimal("450.000"), new BigDecimal("15.000"), BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", "Yangi narx!", "ACTIVE"
        );

        mockMvc.perform(put("/api/v1/market/seller/offers/{id}", offer.getId())
                        .header("Authorization", "Bearer " + supplierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(179000.00))
                .andExpect(jsonPath("$.availableQuantity").value(450.0));

        // Supplier A deactivates their offer
        mockMvc.perform(post("/api/v1/market/seller/offers/{id}/deactivate", offer.getId())
                        .header("Authorization", "Bearer " + supplierTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        SellerOfferEntity updated = sellerOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals("ARCHIVED", updated.getStatus());
    }

    @Test
    @DisplayName("Seller Offer Security: supplier cannot view, modify or deactivate another supplier's offer")
    void shouldPreventSupplierFromModifyingAnotherSuppliersOffer() throws Exception {
        // Offer belongs to Supplier A
        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), verifiedSupplierA.getId(), selitraProduct.getId(),
                new BigDecimal("185000.00"), "UZS", "BAG_50KG",
                new BigDecimal("500.000"), BigDecimal.ZERO, new BigDecimal("10.000"), BigDecimal.ONE,
                "Toshkent viloyati", "Chirchiq", null, "ACTIVE"
        ));

        // Supplier B attempts to view private offer detail -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/seller/offers/{id}", offer.getId())
                        .header("Authorization", "Bearer " + supplierTokenB))
                .andExpect(status().isForbidden());

        // Supplier B attempts to update offer -> 403 Forbidden
        UpdateSellerOfferRequest updateReq = new UpdateSellerOfferRequest(
                new BigDecimal("1000.00"), "UZS", "BAG_50KG", null, null, null, null, null, null, null
        );
        mockMvc.perform(put("/api/v1/market/seller/offers/{id}", offer.getId())
                        .header("Authorization", "Bearer " + supplierTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Supplier B attempts to deactivate offer -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/seller/offers/{id}/deactivate", offer.getId())
                        .header("Authorization", "Bearer " + supplierTokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin Master Catalog: administrator creates new catalog product, non-admin is forbidden")
    void shouldAllowAdminToCreateCatalogProductAndRejectNonAdmin() throws Exception {
        CreateCatalogProductRequest request = new CreateCatalogProductRequest(
                machineryCategory.getId(),
                "Nasos agregati 3.5 kW",
                "Насосный агрегат 3.5 кВт",
                "AQUA-TECH",
                "Suv nasosi elektr motorli",
                "PIECE",
                "Chuqurlikdan suv tortish nasosi"
        );

        // Non-admin (Supplier) attempts -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/catalog/products")
                        .header("Authorization", "Bearer " + supplierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Admin attempts -> 201 Created
        mockMvc.perform(post("/api/v1/market/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nameUz").value("Nasos agregati 3.5 kW"))
                .andExpect(jsonPath("$.categoryCode").value("MACHINERY"));

        assertEquals(2, catalogProductRepository.count());
    }
}
