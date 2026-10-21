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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.hosil.platform.common.security.JwtTokenProvider;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryAllowedUnitEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestAllocationEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryAllowedUnitRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.service.InventoryReservationExpiryService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Milestone 6B.10 — Cross-Flow Integrity Hardening Integration Tests.
 * Verifies cross-flow correctness between wholesale harvest orders (via RFQ)
 * and direct agricultural-input orders (via seller offers) sharing unified
 * order and inventory reservation architectures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossFlowIntegrityHardeningIntegrationTests {

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

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private HarvestListingRepository harvestListingRepository;

    @Autowired
    private HarvestAllocationRepository harvestAllocationRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryOfferRepository inquiryOfferRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private InventoryReservationExpiryService expiryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserEntity supplierA;
    private UserEntity farmerSeller;
    private UserEntity buyer;
    private UserEntity buyer2;

    private String supplierTokenA;
    private String farmerSellerToken;
    private String buyerToken;
    private String buyer2Token;

    private CatalogProductEntity fertilizerProduct;
    private CatalogProductEntity seedProduct;
    private SellerOfferEntity fertilizerOffer;
    private SellerOfferEntity seedOffer;

    private HarvestListingEntity harvestListing;
    private InquiryEntity agreedInquiry;
    private InquiryOfferEntity agreedOffer;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        inventoryReservationRepository.deleteAll();
        orderRepository.deleteAll();
        inquiryOfferRepository.deleteAll();
        inquiryRepository.deleteAll();
        harvestAllocationRepository.deleteAll();
        harvestListingRepository.deleteAll();
        harvestBatchRepository.deleteAll();
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        farmRepository.deleteAll();
        sellerOfferRepository.deleteAll();
        catalogProductRepository.deleteAll();
        categoryAllowedUnitRepository.deleteAll();
        categoryRepository.deleteAll();
        sellerProfileRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Categories
        CategoryEntity inputCat = categoryRepository.save(new CategoryEntity(
                UUID.randomUUID(), null, "FERTILIZER", "Oʻgʻitlar", "Удобрения", 1, "AGRICULTURAL_INPUT", true
        ));
        CategoryEntity harvestCat = categoryRepository.findByCode("HARVEST")
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(
                        UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 2, "HARVEST", true
                )));

        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(inputCat.getId(), "BAG_50KG"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(inputCat.getId(), "KG"));

        // 2. Catalog Products
        fertilizerProduct = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), inputCat.getId(), "Ammiakli selitra", "Аммиачная селитра",
                "MAXAM-CHIRCHIQ", "N 34.4%", "BAG_50KG", "Azotli oʻgʻit", true
        ));

        seedProduct = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), inputCat.getId(), "Bugʻdoy urugʻi", "Семена пшеницы",
                "AGRO-URUGʻ", "Grom elita", "KG", "Yuqori hosildor urugʻ", true
        ));

        // 3. Users & Profiles
        supplierA = userRepository.save(new UserEntity("+998901111111", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(supplierA.getId(), "Anvar Supplier", "Toshkent viloyati", "Chirchiq"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplierA.getId(), "COMMERCIAL_SUPPLIER", "AgroKimyo MCHJ", "111222333", "VERIFIED", Instant.now()
        ));
        supplierTokenA = jwtTokenProvider.generateAccessToken(supplierA.getId(), supplierA.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        farmerSeller = userRepository.save(new UserEntity("+998902222222", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(farmerSeller.getId(), "Bahrom Dehqon", "Samarqand viloyati", "Jomboy"));
        farmerSellerToken = jwtTokenProvider.generateAccessToken(farmerSeller.getId(), farmerSeller.getPhoneNumber(), List.of("REGISTERED_USER", "SELLER"));

        buyer = userRepository.save(new UserEntity("+998903333333", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyer.getId(), "Karim Xaridor", "Jizzax viloyati", "Zomin"));
        buyerToken = jwtTokenProvider.generateAccessToken(buyer.getId(), buyer.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        buyer2 = userRepository.save(new UserEntity("+998904444444", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyer2.getId(), "Otabek Xaridor", "Sirdaryo viloyati", "Guliston"));
        buyer2Token = jwtTokenProvider.generateAccessToken(buyer2.getId(), buyer2.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        // 4. Input Offers (Supplier A)
        fertilizerOffer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplierA.getId(), fertilizerProduct.getId(),
                new BigDecimal("150000.00"), "UZS", "BAG_50KG",
                new BigDecimal("100.000"), new BigDecimal("0.000"),
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Chirchiq", "Yetarli zaxira mavjud", "ACTIVE"
        ));

        seedOffer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplierA.getId(), seedProduct.getId(),
                new BigDecimal("8000.00"), "UZS", "KG",
                new BigDecimal("500.000"), new BigDecimal("0.000"),
                new BigDecimal("50.000"), new BigDecimal("10.000"),
                "Toshkent viloyati", "Chirchiq", "Urugʻlik sertifikati mavjud", "ACTIVE"
        ));

        // 5. Harvest Batch & RFQ Listing (Farmer Bahrom)
        FarmEntity farm = farmRepository.save(new FarmEntity(farmerSeller.getId(), "Samarqand Bogʻlari", "Samarqand viloyati", "Jomboy", new BigDecimal("30.00"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Olmazor bogʻ", new BigDecimal("10.00"), "HECTARE", null, "Olma"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Olma", "Golden", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), "HARVESTED"));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(), farm.getId(), cropCycle.getId(),
                "Olma (Golden)", "FIRST_CLASS",
                new BigDecimal("10000.000"), new BigDecimal("5000.000"),
                "KG", LocalDate.of(2026, 9, 15), "Sovuq ombor", "RECORDED",
                "batch-cross-flow-test", null, 1L
        ));

        harvestListing = harvestListingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                farmerSeller.getId(),
                batch.getId(),
                harvestCat.getId(),
                "Samarqand shirin olmasi",
                "Sara olma",
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("8000.00"),
                "UZS",
                "RFQ",
                true,
                new BigDecimal("5000.000"), // allocated
                new BigDecimal("5000.000"), // available
                BigDecimal.ZERO,           // reserved
                BigDecimal.ZERO,           // sold
                "KG",
                new BigDecimal("500.000"),
                new BigDecimal("50.000"),
                "Samarqand viloyati",
                "Jomboy tumani",
                "PUBLISHED",
                null,
                Instant.now().minusSeconds(1800)
        ));

        harvestAllocationRepository.save(new HarvestAllocationEntity(
                UUID.randomUUID(), batch.getId(), harvestListing.getId(),
                new BigDecimal("5000.000"), "KG", "ALLOCATED", Instant.now()
        ));

        UUID agreedOfferId = UUID.randomUUID();
        InquiryEntity inquiry = new InquiryEntity(
                UUID.randomUUID(),
                harvestListing.getId(),
                buyer.getId(),
                farmerSeller.getId(),
                "Olma (Golden)",
                "KG",
                "inq-cross-flow"
        );
        inquiry.setStatus("AGREED");
        inquiry.setCurrentOfferId(agreedOfferId);
        inquiry.setAgreedOfferId(agreedOfferId);
        inquiry.setAgreedAt(Instant.now().minusSeconds(300));
        inquiry.setAgreedQuantity(new BigDecimal("1000.000"));
        inquiry.setAgreedUnitPrice(new BigDecimal("7500.00"));
        inquiry.setAgreedCurrency("UZS");
        inquiry.setAgreedDeliveryTerms("FCA (Samarqand)");
        agreedInquiry = inquiryRepository.save(inquiry);

        agreedOffer = inquiryOfferRepository.save(new InquiryOfferEntity(
                agreedOfferId,
                agreedInquiry.getId(),
                1,
                buyer.getId(),
                "BUYER",
                new BigDecimal("1000.000"),
                "KG",
                new BigDecimal("7500.00"),
                "UZS",
                "FCA (Samarqand)",
                "1000 kg olamiz",
                Instant.now().plusSeconds(86400),
                "offer-cross-flow"
        ));
    }

    @Test
    @DisplayName("Integrity 1: Conservation equation holds across both flow lifecycles")
    void testInventoryConservationEquationAcrossFlows() throws Exception {
        // --- Flow A: Agricultural-Input Order Placement ---
        CreateInputOrderRequest inputReq = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("20.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Chirchiq ombori", "Ombordan olib ketish", "Tezkor kerak"
        );

        MvcResult inputRes = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderFlowType").value("INPUT_DIRECT_CHECKOUT"))
                .andReturn();

        OrderDetailDto inputOrder = objectMapper.readValue(inputRes.getResponse().getContentAsString(), OrderDetailDto.class);

        // Verify ledger conservation equation on SellerOfferEntity:
        // on_hand = available_quantity + reserved_quantity + sold_quantity
        SellerOfferEntity offerAfterOrder = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), offerAfterOrder.getTotalOnHandQuantity());
        assertEquals(0, new BigDecimal("80.000").compareTo(offerAfterOrder.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("20.000").compareTo(offerAfterOrder.getReservedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(offerAfterOrder.getSoldQuantity()));

        // --- Flow B: Wholesale RFQ Order Placement ---
        CreateWholesaleOrderRequest wholesaleReq = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Jomboy bogʻi", "Yuk mashinasi bilan boraman"
        );

        MvcResult wholesaleRes = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wholesaleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderFlowType").value("HARVEST_PURCHASE_REQUEST"))
                .andReturn();

        OrderDetailDto wholesaleOrder = objectMapper.readValue(wholesaleRes.getResponse().getContentAsString(), OrderDetailDto.class);

        // Verify harvest listing conservation:
        // allocated = available + reserved + sold
        HarvestListingEntity listingAfterOrder = harvestListingRepository.findById(harvestListing.getId()).orElseThrow();
        assertEquals(0, listingAfterOrder.getAllocatedQuantity().compareTo(
                listingAfterOrder.getAvailableQuantity().add(listingAfterOrder.getReservedQuantity()).add(listingAfterOrder.getSoldQuantity())
        ));
        assertEquals(0, new BigDecimal("4000.000").compareTo(listingAfterOrder.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("1000.000").compareTo(listingAfterOrder.getReservedQuantity()));

        // --- Cancellation and Stock Restoration ---
        // Cancel input order as buyer
        mockMvc.perform(post("/api/v1/market/orders/{id}/cancel", inputOrder.id())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Reja oʻzgardi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        SellerOfferEntity offerAfterCancel = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), offerAfterCancel.getTotalOnHandQuantity());
        assertEquals(0, new BigDecimal("100.000").compareTo(offerAfterCancel.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(offerAfterCancel.getReservedQuantity()));

        // Reject wholesale order as seller
        mockMvc.perform(post("/api/v1/market/orders/{id}/reject", wholesaleOrder.id())
                        .header("Authorization", "Bearer " + farmerSellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Omborda taʼmirlash ishlari\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        HarvestListingEntity listingAfterReject = harvestListingRepository.findById(harvestListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5000.000").compareTo(listingAfterReject.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(listingAfterReject.getReservedQuantity()));
    }

    @Test
    @DisplayName("Integrity 2: Concurrent buyer cancellation vs background expiry produces zero double-restoration")
    void testConcurrentBuyerCancellationVsBackgroundExpiryZeroDoubleRestoration() throws Exception {
        // Place an input order for 25 bags
        CreateInputOrderRequest inputReq = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("25.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Toshkent", "Yetkazib berish", "Tezroq"
        );

        MvcResult res = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputReq)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto order = objectMapper.readValue(res.getResponse().getContentAsString(), OrderDetailDto.class);
        UUID reservationId = order.reservations().get(0).id();

        // Artificially set reservation expiry into the past for test sweep eligibility
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            InventoryReservationEntity r = inventoryReservationRepository.findById(reservationId).orElseThrow();
            r.setExpiresAt(Instant.now().minusSeconds(100));
            inventoryReservationRepository.save(r);
        });

        // Concurrently run Buyer Cancellation and Expiry Sweep
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger cancelSuccess = new AtomicInteger();
        AtomicInteger expirySweepSuccess = new AtomicInteger();

        Callable<Void> cancelTask = () -> {
            try {
                MvcResult cRes = mockMvc.perform(post("/api/v1/market/orders/{id}/cancel", order.id())
                                .header("Authorization", "Bearer " + buyerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"Tez bekor qilish\"}"))
                        .andReturn();
                if (cRes.getResponse().getStatus() == 200) {
                    cancelSuccess.incrementAndGet();
                }
            } catch (Exception ignored) {}
            return null;
        };

        Callable<Void> expiryTask = () -> {
            try {
                int swept = expiryService.sweepExpiredReservations(10);
                if (swept > 0) {
                    expirySweepSuccess.incrementAndGet();
                }
            } catch (Exception ignored) {}
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(cancelTask, expiryTask));
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // VERIFICATION:
        // 1. Stock must be restored EXACTLY ONCE (available = 100.000, reserved = 0.000)
        SellerOfferEntity offerFinal = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.000").compareTo(offerFinal.getAvailableQuantity()),
                "Available quantity must be exactly restored to 100, never 125 (double release)");
        assertEquals(0, BigDecimal.ZERO.compareTo(offerFinal.getReservedQuantity()),
                "Reserved quantity must be 0");
        assertEquals(new BigDecimal("100.000"), offerFinal.getTotalOnHandQuantity());

        // 2. Reservation status must be either RELEASED_CANCELLED or RELEASED_EXPIRED
        InventoryReservationEntity finalRes = inventoryReservationRepository.findById(reservationId).orElseThrow();
        assertTrue(
                "RELEASED_CANCELLED".equals(finalRes.getStatus()) || "RELEASED_EXPIRED".equals(finalRes.getStatus()),
                "Reservation must be transitioned to a released status"
        );

        // 3. Order status must be CANCELLED
        OrderEntity finalOrder = orderRepository.findById(order.id()).orElseThrow();
        assertEquals("CANCELLED", finalOrder.getStatus());
    }

    @Test
    @DisplayName("Integrity 3: Multi-item input order expiration restores all items and cancels master order")
    void testMultiItemInputOrderExpirationSweep() throws Exception {
        // Buyer orders 15 bags Selitra AND 100 kg Seed
        CreateInputOrderRequest req = new CreateInputOrderRequest(
                List.of(
                        new CreateInputOrderRequest.InputOrderItemRequest(fertilizerOffer.getId(), new BigDecimal("15.000"), "BAG_50KG"),
                        new CreateInputOrderRequest.InputOrderItemRequest(seedOffer.getId(), new BigDecimal("100.000"), "KG")
                ),
                "BUYER_ARRANGES", "Ombor", "Yetkazib berish", "Ikkalasini birga olib ketaman"
        );

        MvcResult res = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto order = objectMapper.readValue(res.getResponse().getContentAsString(), OrderDetailDto.class);
        assertEquals(2, order.items().size());
        assertEquals(2, order.reservations().size());

        // Verify stock reserved
        SellerOfferEntity fertBefore = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        SellerOfferEntity seedBefore = sellerOfferRepository.findById(seedOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("85.000").compareTo(fertBefore.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("15.000").compareTo(fertBefore.getReservedQuantity()));
        assertEquals(0, new BigDecimal("400.000").compareTo(seedBefore.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("100.000").compareTo(seedBefore.getReservedQuantity()));

        // Set both reservations to expired
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            for (var rDto : order.reservations()) {
                InventoryReservationEntity r = inventoryReservationRepository.findById(rDto.id()).orElseThrow();
                r.setExpiresAt(Instant.now().minusSeconds(60));
                inventoryReservationRepository.save(r);
            }
        });

        // Run expiry worker sweep
        int releasedCount = expiryService.sweepExpiredReservations(10);
        assertEquals(2, releasedCount, "Both reservations must be released by worker");

        // Verify stock restored for BOTH offers
        SellerOfferEntity fertAfter = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        SellerOfferEntity seedAfter = sellerOfferRepository.findById(seedOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.000").compareTo(fertAfter.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fertAfter.getReservedQuantity()));
        assertEquals(0, new BigDecimal("500.000").compareTo(seedAfter.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(seedAfter.getReservedQuantity()));

        // Verify master order is cancelled by SYSTEM
        OrderEntity cancelledOrder = orderRepository.findById(order.id()).orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getStatus());
        assertTrue(cancelledOrder.getCancellationReason().contains("Toʻlov muddati tugaganligi"));
    }

    @Test
    @DisplayName("Integrity 4: Idempotency exact replay returns cached order; payload mismatch returns 409 Conflict")
    void testIdempotencyExactReplayAndMismatchRejection() throws Exception {
        String key = "KEY-INTEGRITY-" + UUID.randomUUID();

        CreateInputOrderRequest req = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("10.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Manzil 1", "Yetkazib berish", "Eslatma"
        );

        // 1. Initial request -> 201 Created
        MvcResult firstRes = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto firstDto = objectMapper.readValue(firstRes.getResponse().getContentAsString(), OrderDetailDto.class);

        // 2. Exact Replay -> 200 OK + IDEMPOTENT_HIT
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andExpect(jsonPath("$.id").value(firstDto.id().toString()));

        // Exactly 1 order in DB
        assertEquals(1, orderRepository.findAll().size());

        // Stock reserved only once (10 reserved, 90 available)
        SellerOfferEntity offer = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90.000").compareTo(offer.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("10.000").compareTo(offer.getReservedQuantity()));

        // 3. Modified payload with SAME key -> 409 Conflict
        CreateInputOrderRequest modifiedReq = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("20.000"), "BAG_50KG" // quantity changed!
                )),
                "BUYER_ARRANGES", "Manzil 1", "Yetkazib berish", "Eslatma"
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifiedReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Still exactly 1 order in DB
        assertEquals(1, orderRepository.findAll().size());
    }

    @Test
    @DisplayName("Integrity 5: Concurrent identical-key requests create exactly 1 order without errors")
    void testConcurrentIdenticalKeySubmissions() throws Exception {
        String key = "KEY-CONCURRENT-" + UUID.randomUUID();

        CreateInputOrderRequest req = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("10.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Toshkent", "Yetkazib berish", "Birgalikda yuborish"
        );

        String reqJson = objectMapper.writeValueAsString(req);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> submitTask = () -> {
            try {
                MvcResult result = mockMvc.perform(post("/api/v1/market/input-orders")
                                .header("Authorization", "Bearer " + buyerToken)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reqJson))
                        .andReturn();
                return result.getResponse().getStatus();
            } catch (Exception ex) {
                return 500;
            }
        };

        List<Future<Integer>> futures = executor.invokeAll(List.of(submitTask, submitTask));
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Both must be successful (either 201 or 200 idempotent replay), none 500
        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 201),
                "Expected all concurrent identical key requests to return 200 or 201, got: " + statuses);

        // Exactly one order exists in database
        assertEquals(1, orderRepository.findAll().size());

        // Stock reserved once
        SellerOfferEntity offer = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90.000").compareTo(offer.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("10.000").compareTo(offer.getReservedQuantity()));
    }

    @Test
    @DisplayName("Integrity 6: Competing buyers for limited stock prevents overselling")
    void testCompetingOrdersForLimitedStockZeroOverselling() throws Exception {
        // Offer only has 10 bags left
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(s -> {
            SellerOfferEntity off = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
            off.setAvailableQuantity(new BigDecimal("10.000"));
            sellerOfferRepository.save(off);
        });

        // Buyer 1 requests 8 bags
        CreateInputOrderRequest reqBuyer1 = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("8.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Manzil 1", "Yetkazib berish", null
        );

        // Buyer 2 requests 8 bags concurrently (total 16 > 10 available)
        CreateInputOrderRequest reqBuyer2 = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("8.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Manzil 2", "Yetkazib berish", null
        );

        String json1 = objectMapper.writeValueAsString(reqBuyer1);
        String json2 = objectMapper.writeValueAsString(reqBuyer2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> t1 = () -> mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json1))
                .andReturn().getResponse().getStatus();

        Callable<Integer> t2 = () -> mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyer2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json2))
                .andReturn().getResponse().getStatus();

        List<Future<Integer>> futures = executor.invokeAll(List.of(t1, t2));
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Exactly one 201 Created and one 409 Conflict
        assertTrue(statuses.contains(201), "One request must succeed");
        assertTrue(statuses.contains(409), "Competing request must fail with 409 Conflict");

        // Available quantity must be 2, reserved must be 8, sum must equal 10 (zero overselling)
        SellerOfferEntity offer = sellerOfferRepository.findById(fertilizerOffer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2.000").compareTo(offer.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("8.000").compareTo(offer.getReservedQuantity()));
        assertEquals(new BigDecimal("10.000"), offer.getTotalOnHandQuantity());
    }

    @Test
    @DisplayName("Integrity 7: Cross-flow coexistence and unified order listing")
    void testCrossFlowCoexistenceAndUnifiedOrderListing() throws Exception {
        // Create an input order
        CreateInputOrderRequest inputReq = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(
                        fertilizerOffer.getId(), new BigDecimal("5.000"), "BAG_50KG"
                )),
                "BUYER_ARRANGES", "Manzil", "Yetkazib berish", null
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputReq)))
                .andExpect(status().isCreated());

        // Create a wholesale RFQ order
        CreateWholesaleOrderRequest wholesaleReq = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Manzil", null
        );

        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wholesaleReq)))
                .andExpect(status().isCreated());

        // List buyer orders -> Both orders should be returned
        mockMvc.perform(get("/api/v1/market/orders?role=BUYER")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].orderNumber", hasItem(startsWith("ORD-INP-"))))
                .andExpect(jsonPath("$.content[*].orderNumber", hasItem(startsWith("ORD-20"))));
    }
}
