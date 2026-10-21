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
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryAllowedUnitEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryAllowedUnitRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.service.InventoryReservationExpiryService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
 * Comprehensive integration tests for Milestone 6B.9 — Agricultural-Input Order Placement and Lifecycle.
 * Verifies all 13 core requirements across order placement, stock conservation, multi-offer orders,
 * validation, idempotency, deterministic concurrency, stage-gated cancellation, expiry sweeps, and authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputOrderPlacementIntegrationTests {

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
    private UserEntity supplierB;
    private UserEntity buyer;
    private UserEntity thirdParty;

    private String supplierTokenA;
    private String supplierTokenB;
    private String buyerToken;
    private String thirdPartyToken;

    private CategoryEntity inputCategory;
    private CatalogProductEntity productA;
    private CatalogProductEntity productB;

    private SellerOfferEntity offerA1;
    private SellerOfferEntity offerA2;
    private SellerOfferEntity offerB1;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        inventoryReservationRepository.deleteAll();
        orderRepository.deleteAll();
        sellerOfferRepository.deleteAll();
        catalogProductRepository.deleteAll();
        categoryAllowedUnitRepository.deleteAll();
        categoryRepository.deleteAll();
        sellerProfileRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Category & Allowed Units
        inputCategory = categoryRepository.save(new CategoryEntity(
                UUID.randomUUID(), null, "FERTILIZER", "Oʻgʻitlar", "Удобрения", 1, "AGRICULTURAL_INPUT", true
        ));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(inputCategory.getId(), "BAG_50KG"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(inputCategory.getId(), "KG"));
        categoryAllowedUnitRepository.save(new CategoryAllowedUnitEntity(inputCategory.getId(), "TON"));

        // 2. Catalog Products
        productA = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), inputCategory.getId(), "Ammiakli selitra", "Аммиачная селитра",
                "MAXAM-CHIRCHIQ", "N 34.4%", "BAG_50KG", "Yuqori sifatli azotli oʻgʻit", true
        ));

        productB = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), inputCategory.getId(), "Karbamid (Mochevina)", "Карбамид",
                "FARGONA-AZOT", "N 46.2%", "BAG_50KG", "Konsentrlangan mineral oʻgʻit", true
        ));

        // 3. Users & Tokens
        supplierA = userRepository.save(new UserEntity("+998901110001", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(supplierA.getId(), "Anvar Sotuvchi", "Toshkent viloyati", "Chirchiq"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplierA.getId(), "COMMERCIAL_SUPPLIER", "Chirchiq Kimyo MCHJ", "123456789", "VERIFIED", Instant.now()
        ));
        supplierTokenA = jwtTokenProvider.generateAccessToken(supplierA.getId(), supplierA.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        supplierB = userRepository.save(new UserEntity("+998901110002", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(supplierB.getId(), "Bobur Savdo", "Samarqand viloyati", "Jomboy"));
        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplierB.getId(), "COMMERCIAL_SUPPLIER", "Samarqand Agro XK", "987654321", "VERIFIED", Instant.now()
        ));
        supplierTokenB = jwtTokenProvider.generateAccessToken(supplierB.getId(), supplierB.getPhoneNumber(), List.of("REGISTERED_USER", "SUPPLIER"));

        buyer = userRepository.save(new UserEntity("+998902220001", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyer.getId(), "Karim Fermer", "Jizzax viloyati", "Zomin"));
        buyerToken = jwtTokenProvider.generateAccessToken(buyer.getId(), buyer.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        thirdParty = userRepository.save(new UserEntity("+998903330001", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(thirdParty.getId(), "Tashqi Begona", "Navoiy viloyati", "Karmana"));
        thirdPartyToken = jwtTokenProvider.generateAccessToken(thirdParty.getId(), thirdParty.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        // 4. Seller Offers
        // Supplier A - Offer 1 (Selitra, 100 bags available, price 150,000 UZS)
        offerA1 = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplierA.getId(), productA.getId(),
                new BigDecimal("150000.00"), "UZS", "BAG_50KG",
                new BigDecimal("100.000"), new BigDecimal("0.000"),
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Chirchiq",
                "Omborda yetarli miqdorda mavjud", "ACTIVE"
        ));

        // Supplier A - Offer 2 (Karbamid, 50 bags available, price 220,000 UZS)
        offerA2 = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplierA.getId(), productB.getId(),
                new BigDecimal("220000.00"), "UZS", "BAG_50KG",
                new BigDecimal("50.000"), new BigDecimal("0.000"),
                new BigDecimal("2.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Chirchiq",
                "Karbamid sifat sertifikati bilan", "ACTIVE"
        ));

        // Supplier B - Offer 1 (Selitra from different seller)
        offerB1 = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplierB.getId(), productA.getId(),
                new BigDecimal("148000.00"), "UZS", "BAG_50KG",
                new BigDecimal("80.000"), new BigDecimal("0.000"),
                new BigDecimal("10.000"), new BigDecimal("5.000"),
                "Samarqand viloyati", "Jomboy",
                "Samarqand omboridan yetkazish", "ACTIVE"
        ));
    }

    @Test
    @DisplayName("Scenario 1: Successful single-item input order creation with temporary reservation")
    void scenario01_successfulSingleItemInputOrderCreation() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("10.000"), "BAG_50KG")),
                "BUYER_ARRANGES",
                "Toshkent viloyati, Chirchiq ombori",
                "Olib ketish sharti bilan",
                "Tezroq yuklash soʻraladi"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING_PAYMENT")))
                .andExpect(jsonPath("$.orderFlowType", is("INPUT_DIRECT_CHECKOUT")))
                .andExpect(jsonPath("$.totalAmount", is(1500000.0)))
                .andExpect(jsonPath("$.currency", is("UZS")))
                .andExpect(jsonPath("$.buyerUserId", is(buyer.getId().toString())))
                .andExpect(jsonPath("$.sellerUserId", is(supplierA.getId().toString())))
                .andExpect(jsonPath("$.paymentExpiresAt", notNullValue()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].sellerOfferId", is(offerA1.getId().toString())))
                .andExpect(jsonPath("$.items[0].catalogProductId", is(productA.getId().toString())))
                .andExpect(jsonPath("$.items[0].quantity", is(10.0)))
                .andExpect(jsonPath("$.items[0].unitPrice", is(150000.0)))
                .andExpect(jsonPath("$.items[0].subtotalAmount", is(1500000.0)))
                .andExpect(jsonPath("$.reservation.status", is("ACTIVE")))
                .andExpect(jsonPath("$.reservation.reservedQuantity", is(10.0)))
                .andReturn();

        OrderDetailDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), OrderDetailDto.class);

        // Verify offer inventory conservation in DB: available = 90, reserved = 10, sold = 0
        SellerOfferEntity updatedOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), updatedOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("10.000"), updatedOffer.getReservedQuantity());
        assertEquals(new BigDecimal("0.000"), updatedOffer.getSoldQuantity());

        // Conservation equation: available + reserved + sold == total (100)
        assertEquals(new BigDecimal("100.000"),
                updatedOffer.getAvailableQuantity().add(updatedOffer.getReservedQuantity()).add(updatedOffer.getSoldQuantity()));

        // Verify order entity
        OrderEntity order = orderRepository.findById(dto.id()).orElseThrow();
        assertNull(order.getHarvestListingId());
        assertEquals("INPUT_DIRECT_CHECKOUT", order.getOrderFlowType());
        assertEquals("PENDING_PAYMENT", order.getStatus());

        // Verify reservation
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(order.getId());
        assertEquals(1, reservations.size());
        assertEquals(offerA1.getId(), reservations.get(0).getSellerOfferId());
        assertNull(reservations.get(0).getHarvestListingId());
        assertEquals("ACTIVE", reservations.get(0).getStatus());
    }

    @Test
    @DisplayName("Scenario 2: Multiple offers from same seller in single order")
    void scenario02_multipleOffersFromSameSellerInSingleOrder() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(
                        new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("10.000"), "BAG_50KG"),
                        new CreateInputOrderRequest.InputOrderItemRequest(offerA2.getId(), new BigDecimal("5.000"), "BAG_50KG")
                ),
                "SELLER_DELIVERS",
                "Zomin tumani, Dashtobod",
                "Yetkazib berish bilan",
                "Birgalikda keltirilsin"
        );

        // Subtotal: 10 * 150000 = 1,500,000; 5 * 220000 = 1,100,000; Total: 2,600,000.00 UZS
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING_PAYMENT")))
                .andExpect(jsonPath("$.totalAmount", is(2600000.0)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.reservations", hasSize(2)));

        SellerOfferEntity updatedA1 = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), updatedA1.getAvailableQuantity());
        assertEquals(new BigDecimal("10.000"), updatedA1.getReservedQuantity());

        SellerOfferEntity updatedA2 = sellerOfferRepository.findById(offerA2.getId()).orElseThrow();
        assertEquals(new BigDecimal("45.000"), updatedA2.getAvailableQuantity());
        assertEquals(new BigDecimal("5.000"), updatedA2.getReservedQuantity());
    }

    @Test
    @DisplayName("Scenario 3: Rejection of mixed-seller orders (BusinessConflictException -> 409)")
    void scenario03_rejectionOfMixedSellerOrders() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(
                        new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("5.000"), "BAG_50KG"),
                        new CreateInputOrderRequest.InputOrderItemRequest(offerB1.getId(), new BigDecimal("10.000"), "BAG_50KG")
                ),
                "BUYER_ARRANGES", null, null, null
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("Single-seller order policy")));

        // Zero orders or reservations created
        assertEquals(0, orderRepository.count());
        assertEquals(0, inventoryReservationRepository.count());
    }

    @Test
    @DisplayName("Scenario 4: Rejection of inactive offers (PAUSED, OUT_OF_STOCK)")
    void scenario04_rejectionOfInactiveOffers() throws Exception {
        offerA1.setStatus("PAUSED");
        sellerOfferRepository.save(offerA1);

        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("5.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("faol emas")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Scenario 5: Rejection of insufficient stock")
    void scenario05_rejectionOfInsufficientStock() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("105.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("Yetarli miqdorda mahsulot mavjud emas")));

        assertEquals(0, orderRepository.count());
        // Verify stock remains untouched
        SellerOfferEntity currentOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), currentOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("0.000"), currentOffer.getReservedQuantity());
    }

    @Test
    @DisplayName("Scenario 6: Rejection of self-ordering (buyer == seller)")
    void scenario06_rejectionOfSelfOrdering() throws Exception {
        // Supplier A tries to purchase from Supplier A's own offer
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("5.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + supplierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("Self-ordering is forbidden")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Scenario 7: Unit mismatch, MOQ, and step quantity validation errors (HTTP 400)")
    void scenario07_unitMismatchAndMoqAndStepValidationErrors() throws Exception {
        // 7a. Unit mismatch: Offer expects "BAG_50KG", buyer requests "KG"
        CreateInputOrderRequest unitMismatchRequest = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("10.000"), "KG")),
                "BUYER_ARRANGES", null, null, null
        );
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unitMismatchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("Oʻlchov birligi mos emas")));

        // 7b. Qty below MOQ: offerB1 minOrder is 10, buyer requests 5
        CreateInputOrderRequest moqRequest = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerB1.getId(), new BigDecimal("5.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moqRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("minimal buyurtma miqdoridan kam")));

        // 7c. Step quantity violation: offerB1 minOrder is 10, step is 5. Excess over MOQ must be multiple of 5.
        // Request qty = 12 (excess = 2, which is not divisible by 5)
        CreateInputOrderRequest stepRequest = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerB1.getId(), new BigDecimal("12.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stepRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("qadami mos emas")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Scenario 8: Server-side price calculation accuracy (client sends no price)")
    void scenario08_serverSidePriceCalculationAccuracy() throws Exception {
        // Offer A1 price is 150000.00 UZS. Buyer sends only quantity 7.000
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("7.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        // Expected: 7 * 150000.00 = 1050000.00 UZS
        mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount", is(1050000.0)))
                .andExpect(jsonPath("$.items[0].unitPrice", is(150000.0)))
                .andExpect(jsonPath("$.items[0].subtotalAmount", is(1050000.0)));
    }

    @Test
    @DisplayName("Scenario 9: Idempotent creation on duplicate Idempotency-Key")
    void scenario09_idempotentCreationOnDuplicateKey() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("10.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        String key = "idem-input-" + UUID.randomUUID();

        // Call 1 -> 201 Created
        MvcResult firstCall = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("X-Cache-Lookup"))
                .andReturn();

        OrderDetailDto firstDto = objectMapper.readValue(firstCall.getResponse().getContentAsString(), OrderDetailDto.class);

        // Call 2 with identical key -> 200 OK + X-Cache-Lookup: IDEMPOTENT_HIT
        MvcResult secondCall = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andReturn();

        OrderDetailDto secondDto = objectMapper.readValue(secondCall.getResponse().getContentAsString(), OrderDetailDto.class);

        assertEquals(firstDto.id(), secondDto.id());
        assertEquals(firstDto.orderNumber(), secondDto.orderNumber());

        // Stock was reserved only once (100 - 10 = 90)
        SellerOfferEntity currentOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), currentOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("10.000"), currentOffer.getReservedQuantity());

        // Exactly 1 order in DB
        assertEquals(1, orderRepository.count());
    }

    @Test
    @DisplayName("Scenario 10: Concurrency race for limited stock (zero overselling)")
    void scenario10_concurrencyRaceForLimitedStock() throws Exception {
        // Set available stock to 10
        offerA1.setAvailableQuantity(new BigDecimal("10.000"));
        sellerOfferRepository.save(offerA1);

        // Create Buyer 2
        UserEntity buyer2 = userRepository.save(new UserEntity("+998902220002", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyer2.getId(), "Buyer 2", "Samarqand", "Jomboy"));
        String buyerToken2 = jwtTokenProvider.generateAccessToken(buyer2.getId(), buyer2.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("8.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Callable<Void> task1 = () -> {
            try {
                mockMvc.perform(post("/api/v1/market/input-orders")
                                .header("Authorization", "Bearer " + buyerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 201) successCount.incrementAndGet();
                            else if (status == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception ignored) {}
            return null;
        };

        Callable<Void> task2 = () -> {
            try {
                mockMvc.perform(post("/api/v1/market/input-orders")
                                .header("Authorization", "Bearer " + buyerToken2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 201) successCount.incrementAndGet();
                            else if (status == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception ignored) {}
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent buyer should succeed");
        assertEquals(1, conflictCount.get(), "Second concurrent buyer must receive 409 Conflict");

        // Verify offer stock conservation: remaining available = 2, reserved = 8
        SellerOfferEntity finalOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("2.000"), finalOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("8.000"), finalOffer.getReservedQuantity());
        assertEquals(new BigDecimal("10.000"),
                finalOffer.getAvailableQuantity().add(finalOffer.getReservedQuantity()).add(finalOffer.getSoldQuantity()));
    }

    @Test
    @DisplayName("Scenario 11: Order cancellation releasing reserved stock")
    void scenario11_orderCancellationReleasingReservedStock() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("15.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto createdDto = objectMapper.readValue(createResult.getResponse().getContentAsString(), OrderDetailDto.class);

        // Cancel order by buyer
        mockMvc.perform(post("/api/v1/market/input-orders/{orderId}/cancel", createdDto.id())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Reja oʻzgardi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancellationReason", is("Reja oʻzgardi")));

        // Verify reservation status changed to RELEASED_CANCELLED
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(createdDto.id());
        assertEquals(1, reservations.size());
        assertEquals("RELEASED_CANCELLED", reservations.get(0).getStatus());

        // Verify offer stock is completely restored: available = 100, reserved = 0
        SellerOfferEntity restoredOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), restoredOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("0.000"), restoredOffer.getReservedQuantity());
    }

    @Test
    @DisplayName("Scenario 12: Background expiry sweep releasing input-offer reservation")
    void scenario12_backgroundExpirySweepReleasingInputOfferReservation() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("20.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto createdDto = objectMapper.readValue(createResult.getResponse().getContentAsString(), OrderDetailDto.class);

        // Artificially age the reservation and order payment expiry to the past
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<InventoryReservationEntity> reservations = inventoryReservationRepository.findAllByOrderId(createdDto.id());
            for (InventoryReservationEntity res : reservations) {
                res.setExpiresAt(Instant.now().minus(Duration.ofMinutes(15)));
                inventoryReservationRepository.save(res);
            }
            OrderEntity order = orderRepository.findById(createdDto.id()).orElseThrow();
            order.setPaymentExpiresAt(Instant.now().minus(Duration.ofMinutes(15)));
            orderRepository.save(order);
        });

        // Trigger reservation expiry sweep
        int releasedCount = expiryService.sweepExpiredReservations(10);
        assertEquals(1, releasedCount, "Expired input reservation must be swept and released");

        // Verify order transitioned to CANCELLED
        OrderEntity cancelledOrder = orderRepository.findById(createdDto.id()).orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getStatus());
        assertTrue(cancelledOrder.getCancellationReason().contains("Toʻlov muddati tugagan"));

        // Verify reservation status is RELEASED_EXPIRED
        List<InventoryReservationEntity> sweptReservations = inventoryReservationRepository.findAllByOrderId(createdDto.id());
        assertEquals(1, sweptReservations.size());
        assertEquals("RELEASED_EXPIRED", sweptReservations.get(0).getStatus());

        // Verify offer inventory is restored to full stock
        SellerOfferEntity restoredOffer = sellerOfferRepository.findById(offerA1.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), restoredOffer.getAvailableQuantity());
        assertEquals(new BigDecimal("0.000"), restoredOffer.getReservedQuantity());
    }

    @Test
    @DisplayName("Scenario 13: Unauthorized access checks on order details and history")
    void scenario13_unauthorizedAccessChecksOnOrderDetailsAndHistory() throws Exception {
        CreateInputOrderRequest request = new CreateInputOrderRequest(
                List.of(new CreateInputOrderRequest.InputOrderItemRequest(offerA1.getId(), new BigDecimal("5.000"), "BAG_50KG")),
                "BUYER_ARRANGES", null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/market/input-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderDetailDto createdDto = objectMapper.readValue(createResult.getResponse().getContentAsString(), OrderDetailDto.class);

        // 13a. Third party caller (neither buyer nor seller) cannot view details -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/input-orders/{orderId}", createdDto.id())
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // 13b. Third party caller cannot view order history -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/input-orders/{orderId}/history", createdDto.id())
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // 13c. Third party caller cannot cancel the order -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/input-orders/{orderId}/cancel", createdDto.id())
                        .header("Authorization", "Bearer " + thirdPartyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Begona bekor qilish\"}"))
                .andExpect(status().isForbidden());

        // 13d. Legitimate buyer CAN view details -> 200 OK
        mockMvc.perform(get("/api/v1/market/input-orders/{orderId}", createdDto.id())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdDto.id().toString())));

        // 13e. Legitimate seller CAN view details -> 200 OK
        mockMvc.perform(get("/api/v1/market/input-orders/{orderId}", createdDto.id())
                        .header("Authorization", "Bearer " + supplierTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdDto.id().toString())));
    }
}
