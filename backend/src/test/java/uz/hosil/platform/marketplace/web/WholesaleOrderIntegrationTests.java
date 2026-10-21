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
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WholesaleOrderIntegrationTests {

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
    private HarvestAllocationRepository allocationRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryOfferRepository inquiryOfferRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    private UserEntity sellerUser;
    private UserEntity buyerUser;
    private UserEntity thirdPartyUser;

    private String sellerToken;
    private String buyerToken;
    private String thirdPartyToken;

    private HarvestListingEntity rfqListing;
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
        allocationRepository.deleteAll();
        listingRepository.deleteAll();
        harvestBatchRepository.deleteAll();
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Root Category
        CategoryEntity category = categoryRepository.findByCode("HARVEST")
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(
                        UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 1
                )));

        // 2. Users & Tokens
        sellerUser = userRepository.save(new UserEntity("+998901112233", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(sellerUser.getId(), "Bahrom Dehqon", "Samarqand viloyati", "Jomboy"));
        sellerToken = jwtTokenProvider.generateAccessToken(sellerUser.getId(), sellerUser.getPhoneNumber(), List.of("REGISTERED_USER", "SELLER"));

        buyerUser = userRepository.save(new UserEntity("+998904445566", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyerUser.getId(), "Nodir Xaridor", "Toshkent shahri", "Mirzo Ulugʻbek"));
        buyerToken = jwtTokenProvider.generateAccessToken(buyerUser.getId(), buyerUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        thirdPartyUser = userRepository.save(new UserEntity("+998907778899", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(thirdPartyUser.getId(), "Olim Tashqi", "Fargʻona viloyati", "Quva"));
        thirdPartyToken = jwtTokenProvider.generateAccessToken(thirdPartyUser.getId(), thirdPartyUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        // 3. Farm & Harvest Batch
        FarmEntity farm = farmRepository.save(new FarmEntity(sellerUser.getId(), "Samarqand Bogʻlari", "Samarqand viloyati", "Jomboy", new BigDecimal("40.00"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Olmazor 3-uchastka", new BigDecimal("10.00"), "HECTARE", null, "Organik olma bogʻi"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Olma", "Golden Delicious", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), "HARVESTED"));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                cropCycle.getId(),
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("20000.000"),
                new BigDecimal("15000.000"),
                "KG",
                LocalDate.of(2026, 9, 10),
                "Sovutgichli ombor №2",
                "RECORDED",
                "batch-order-test",
                null,
                1L
        ));

        // 4. RFQ-Enabled Published Listing
        rfqListing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                category.getId(),
                "Eksportbop Golden Olma",
                "Saralangan sifatli olma",
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("12000.00"),
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

        // 5. Agreed RFQ Inquiry & Offer
        UUID agreedOfferId = UUID.randomUUID();
        InquiryEntity inquiry = new InquiryEntity(
                UUID.randomUUID(),
                rfqListing.getId(),
                buyerUser.getId(),
                sellerUser.getId(),
                "Olma (Golden)",
                "KG",
                "inq-idemp-seed"
        );
        inquiry.setStatus("AGREED");
        inquiry.setCurrentOfferId(agreedOfferId);
        inquiry.setAgreedOfferId(agreedOfferId);
        inquiry.setAgreedAt(Instant.now().minusSeconds(300));
        inquiry.setAgreedQuantity(new BigDecimal("1500.000"));
        inquiry.setAgreedUnitPrice(new BigDecimal("11200.00"));
        inquiry.setAgreedCurrency("UZS");
        inquiry.setAgreedDeliveryTerms("FCA (Samarqand)");
        agreedInquiry = inquiryRepository.save(inquiry);

        agreedOffer = inquiryOfferRepository.save(new InquiryOfferEntity(
                agreedOfferId,
                agreedInquiry.getId(),
                1,
                buyerUser.getId(),
                "BUYER",
                new BigDecimal("1500.000"),
                "KG",
                new BigDecimal("11200.00"),
                "UZS",
                "FCA (Samarqand)",
                "1500 kg olamiz",
                Instant.now().plusSeconds(86400),
                "offer-idemp-seed"
        ));
    }

    @Test
    @DisplayName("Should create wholesale order from agreed RFQ and reserve inventory atomically with conservation")
    void shouldCreateOrderFromAgreedRfqAndReserveInventoryAtomically() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(),
                "BUYER_ARRANGES",
                "Toshkent viloyati, Yangiyoʻl tumani, Guliston MFY",
                "Ertalab soat 8:00 da yuklashga transport boradi"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.buyerUserId").value(buyerUser.getId().toString()))
                .andExpect(jsonPath("$.sellerUserId").value(sellerUser.getId().toString()))
                .andExpect(jsonPath("$.harvestListingId").value(rfqListing.getId().toString()))
                .andExpect(jsonPath("$.orderFlowType").value("HARVEST_PURCHASE_REQUEST"))
                .andExpect(jsonPath("$.status").value("AWAITING_SELLER_CONFIRMATION"))
                .andExpect(jsonPath("$.deliveryType").value("BUYER_ARRANGES"))
                .andExpect(jsonPath("$.totalAmount").value(16800000.00)) // 1500 * 11200
                .andExpect(jsonPath("$.currency").value("UZS"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(1500.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(11200.0))
                .andExpect(jsonPath("$.items[0].subtotalAmount").value(16800000.00))
                .andExpect(jsonPath("$.reservation.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reservation.reservedQuantity").value(1500.0))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].toStatus").value("AWAITING_SELLER_CONFIRMATION"))
                .andExpect(jsonPath("$.history[0].actorRole").value("BUYER"))
                .andReturn();

        // Verify database inventory conservation: allocated = available + reserved + sold
        HarvestListingEntity updatedListing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3500.000").compareTo(updatedListing.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("1500.000").compareTo(updatedListing.getReservedQuantity()));
        assertEquals(0, new BigDecimal("0.000").compareTo(updatedListing.getSoldQuantity()));
        assertEquals(0, updatedListing.getAllocatedQuantity().compareTo(
                updatedListing.getAvailableQuantity().add(updatedListing.getReservedQuantity()).add(updatedListing.getSoldQuantity())
        ));
    }

    @Test
    @DisplayName("Should return 200 OK with IDEMPOTENT_HIT on idempotent replay without double-reserving")
    void shouldReturnIdempotentHitWhenReplayingWithSameIdempotencyKey() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(),
                "BUYER_ARRANGES",
                "Toshkent",
                "Test notes"
        );
        String idempotencyKey = "key-" + UUID.randomUUID();

        // 1. Initial order creation
        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_SELLER_CONFIRMATION"));

        // Check stock after first creation: available 3500, reserved 1500
        HarvestListingEntity midListing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3500.000").compareTo(midListing.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("1500.000").compareTo(midListing.getReservedQuantity()));

        // 2. Replay with exact same idempotency key
        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andExpect(jsonPath("$.status").value("AWAITING_SELLER_CONFIRMATION"));

        // Verify stock has NOT been deducted again
        HarvestListingEntity endListing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3500.000").compareTo(endListing.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("1500.000").compareTo(endListing.getReservedQuantity()));
    }

    @Test
    @DisplayName("Should reject duplicate order creation attempt on the same agreed RFQ")
    void shouldRejectDuplicateOrderAttemptOnSameAgreedRfq() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(),
                "BUYER_ARRANGES",
                "Toshkent",
                "Initial order"
        );

        // 1. First order succeeds
        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // 2. Second attempt on the same inquiry without matching idempotency key is rejected
        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Should reject order creation when available stock is insufficient")
    void shouldRejectOrderCreationWhenStockIsInsufficient() throws Exception {
        // Artificially reduce listing available quantity to 1000 kg (agreed offer is 1500 kg)
        rfqListing.setAvailableQuantity(new BigDecimal("1000.000"));
        listingRepository.save(rfqListing);

        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(),
                "BUYER_ARRANGES",
                "Toshkent",
                "Stock shortage test"
        );

        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("INSUFFICIENT_STOCK_AVAILABLE")));

        // Verify no order or reservation was created
        assertTrue(orderRepository.findAll().isEmpty());
        assertTrue(inventoryReservationRepository.findAll().isEmpty());

        // Listing stock remains unchanged at 1000 available, 0 reserved
        HarvestListingEntity unchangedListing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.000").compareTo(unchangedListing.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(unchangedListing.getReservedQuantity()));
    }

    @Test
    @DisplayName("Should allow seller to confirm order and transition status to CONFIRMED (DEC-006)")
    void shouldAllowSellerToConfirmOrder() throws Exception {
        // 1. Buyer creates order
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // 2. Seller confirms order
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/confirm", orderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.paymentExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[1].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.history[1].actorRole").value("SELLER"));

        // Order is now binding
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CONFIRMED", order.getStatus());
        assertNotNull(order.getConfirmedAt());
        assertNotNull(order.getPaymentExpiresAt());
    }

    @Test
    @DisplayName("Should reject repeated seller confirmation with 409 Conflict")
    void shouldRejectRepeatedConfirmation() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // First confirmation succeeds
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/confirm", orderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // Second confirmation is rejected
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/confirm", orderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Should allow seller to reject order and release reserved stock back to available")
    void shouldAllowSellerToRejectOrderAndReleaseInventory() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        OrderDecisionRequest decision = new OrderDecisionRequest("Ombordagi sifat tekshiruvidan oʻtmadi");

        // Seller rejects
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/reject", orderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ombordagi sifat tekshiruvidan oʻtmadi"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].actorRole").value("SELLER"));

        // Inventory reservation must be RELEASED_CANCELLED and stock returned to available
        InventoryReservationEntity reservation = inventoryReservationRepository.findByOrderId(orderId).orElseThrow();
        assertEquals("RELEASED_CANCELLED", reservation.getStatus());

        HarvestListingEntity listing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5000.000").compareTo(listing.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(listing.getReservedQuantity()));
    }

    @Test
    @DisplayName("Should allow buyer to cancel order before seller confirmation and release inventory")
    void shouldAllowBuyerToCancelOrderBeforeSellerConfirmation() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        OrderDecisionRequest cancelReq = new OrderDecisionRequest("Rejalar oʻzgardi");

        // Buyer cancels
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/cancel", orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Rejalar oʻzgardi"))
                .andExpect(jsonPath("$.history[1].actorRole").value("BUYER"));

        // Stock restored
        HarvestListingEntity listing = listingRepository.findById(rfqListing.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5000.000").compareTo(listing.getAvailableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(listing.getReservedQuantity()));
    }

    @Test
    @DisplayName("Should reject buyer cancellation after order has been confirmed (DEC-010)")
    void shouldRejectBuyerCancellationAfterOrderConfirmed() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // Seller confirms order
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/confirm", orderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // Buyer attempts to cancel confirmed order
        OrderDecisionRequest cancelReq = new OrderDecisionRequest("Xaridor bekor qilmoqchi");
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/cancel", orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Should enforce privacy: third-party user cannot view or act on another party's order")
    void shouldEnforcePrivacyAndAuthorizationBoundaries() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        String responseJson = mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // Third-party views order details -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // Third-party views order history -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/orders/{orderId}/history", orderId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // Third-party attempts to confirm order -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/confirm", orderId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // Third-party attempts to cancel order -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/orders/{orderId}/cancel", orderId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should list paginated orders filtered by role and status")
    void shouldListOrdersFilteredByRoleAndStatus() throws Exception {
        CreateWholesaleOrderRequest request = new CreateWholesaleOrderRequest(
                agreedInquiry.getId(), "BUYER_ARRANGES", "Toshkent", "Notes"
        );
        mockMvc.perform(post("/api/v1/market/orders/from-rfq")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Buyer dashboard
        mockMvc.perform(get("/api/v1/market/orders?role=BUYER")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].cropType").value("Olma (Golden)"))
                .andExpect(jsonPath("$.content[0].status").value("AWAITING_SELLER_CONFIRMATION"));

        // Seller dashboard
        mockMvc.perform(get("/api/v1/market/orders?role=SELLER")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].cropType").value("Olma (Golden)"));

        // Third-party sees 0 orders
        mockMvc.perform(get("/api/v1/market/orders?role=BUYER")
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
