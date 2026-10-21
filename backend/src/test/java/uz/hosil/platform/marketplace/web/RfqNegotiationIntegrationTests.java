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
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RfqNegotiationIntegrationTests {

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

    private UserEntity sellerUser;
    private UserEntity buyerUser;
    private UserEntity thirdPartyUser;

    private String sellerToken;
    private String buyerToken;
    private String thirdPartyToken;

    private HarvestListingEntity rfqListing;
    private HarvestListingEntity nonRfqListing;
    private HarvestListingEntity draftListing;

    @BeforeEach
    void setUp() {
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

        // 2. Users
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
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Olmazor 3-uchastka", new BigDecimal("10.00"), "HECTARE", "{\"type\":\"Point\",\"coordinates\":[66.9,39.6]}", "Maxfiy oʻgʻitlar: Organika"));
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
                "Sovutgichli ombor №2 (Private warehouse note)",
                "RECORDED",
                "batch-rfq-test",
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
                true, // isRfqEnabled
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("500.000"), // minOrderQuantity
                new BigDecimal("50.000"),  // stepQuantity
                "Samarqand viloyati",
                "Jomboy tumani",
                "PUBLISHED",
                null,
                Instant.now().minusSeconds(1800)
        ));

        // 5. Non-RFQ Fixed-Price Published Listing
        nonRfqListing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                category.getId(),
                "Standart Olma",
                "Odatdagi narxda",
                "Olma (Golden)",
                "STANDARD",
                new BigDecimal("9000.00"),
                "UZS",
                "FIXED",
                false, // isRfqEnabled = false
                new BigDecimal("2000.000"),
                new BigDecimal("2000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Samarqand viloyati",
                "Jomboy tumani",
                "PUBLISHED",
                null,
                Instant.now().minusSeconds(1200)
        ));

        // 6. Draft Listing
        draftListing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                category.getId(),
                "Qoralama Olma",
                "Chala eʼlon",
                "Olma (Golden)",
                "STANDARD",
                new BigDecimal("8000.00"),
                "UZS",
                "RFQ",
                true,
                new BigDecimal("1000.000"),
                new BigDecimal("1000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Samarqand viloyati",
                "Jomboy tumani",
                "DRAFT",
                null,
                Instant.now().minusSeconds(600)
        ));
    }

    @Test
    @DisplayName("Buyer submits inquiry on RFQ listing -> 201 Created with initial offer #1")
    void submitInquiry_asBuyer_success() throws Exception {
        CreateInquiryRequest request = new CreateInquiryRequest(
                new BigDecimal("1000.000"),
                "KG",
                new BigDecimal("11500.00"),
                "UZS",
                "EXW (Jomboy omboridan olib ketish)",
                "1000 kg olma uchun taklif",
                Instant.now().plusSeconds(86400)
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", "idemp-inq-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.harvestListingId").value(rfqListing.getId().toString()))
                .andExpect(jsonPath("$.buyerUserId").value(buyerUser.getId().toString()))
                .andExpect(jsonPath("$.sellerUserId").value(sellerUser.getId().toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.currentOffer.offerNumber").value(1))
                .andExpect(jsonPath("$.currentOffer.proposerRole").value("BUYER"))
                .andExpect(jsonPath("$.currentOffer.quantity").value(1000.000))
                .andExpect(jsonPath("$.currentOffer.unitPrice").value(11500.00))
                .andExpect(jsonPath("$.currentOffer.totalPrice").value(11500000.00))
                .andExpect(jsonPath("$.currentOffer.status").value("PROPOSED"))
                .andExpect(jsonPath("$.offers", hasSize(1)));
    }

    @Test
    @DisplayName("Submit inquiry with Idempotency-Key replay -> 200 OK with IDEMPOTENT_HIT header")
    void submitInquiry_idempotentHit_returnsOkWithHeader() throws Exception {
        CreateInquiryRequest request = new CreateInquiryRequest(
                new BigDecimal("1000.000"),
                "KG",
                new BigDecimal("11500.00"),
                "UZS",
                "EXW",
                "Idempotent test",
                null
        );

        String firstResponse = mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", "idemp-inq-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inquiryId = objectMapper.readTree(firstResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", "idemp-inq-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"))
                .andExpect(jsonPath("$.id").value(inquiryId));
    }

    @Test
    @DisplayName("Seller attempts inquiry on own listing -> 409 Conflict")
    void submitInquiry_bySellerOnOwnListing_failsWithConflict() throws Exception {
        CreateInquiryRequest request = new CreateInquiryRequest(
                new BigDecimal("600.000"),
                "KG",
                new BigDecimal("11000.00"),
                "UZS",
                "FCA",
                "Oʻz mahsulotiga soʻrov",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Inquiry on non-RFQ listing -> 409 Conflict")
    void submitInquiry_onNonRfqListing_failsWithConflict() throws Exception {
        CreateInquiryRequest request = new CreateInquiryRequest(
                new BigDecimal("200.000"),
                "KG",
                new BigDecimal("8500.00"),
                "UZS",
                "EXW",
                "Non-RFQ eʼlonga soʻrov",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", nonRfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Inquiry on non-published (DRAFT) listing -> 409 Conflict")
    void submitInquiry_onDraftListing_failsWithConflict() throws Exception {
        CreateInquiryRequest request = new CreateInquiryRequest(
                new BigDecimal("200.000"),
                "KG",
                new BigDecimal("8000.00"),
                "UZS",
                "EXW",
                "Draft eʼlonga soʻrov",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", draftListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Quantity less than minOrderQuantity or exceeding availableQuantity -> 409 Conflict")
    void submitInquiry_invalidQuantity_failsWithConflict() throws Exception {
        // Less than minOrderQuantity (500 KG)
        CreateInquiryRequest tooLow = new CreateInquiryRequest(
                new BigDecimal("200.000"),
                "KG",
                new BigDecimal("11000.00"),
                "UZS",
                "EXW",
                "Juda kam miqdor",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLow)))
                .andExpect(status().isConflict());

        // Exceeds availableQuantity (5000 KG)
        CreateInquiryRequest tooHigh = new CreateInquiryRequest(
                new BigDecimal("6000.000"),
                "KG",
                new BigDecimal("11000.00"),
                "UZS",
                "EXW",
                "Juda koʻp miqdor",
                null
        );

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooHigh)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Sequential counteroffer exchange, turn enforcement, and mutual agreement")
    void rfqFullTurnTakingAndAcceptanceWorkflow() throws Exception {
        // 1. Buyer creates initial unpriced inquiry
        CreateInquiryRequest initialInquiry = new CreateInquiryRequest(
                new BigDecimal("1500.000"),
                "KG",
                null, // Unpriced inquiry (request for quote)
                "UZS",
                "FCA (Samarqand)",
                "Iltimos, 1500 kg uchun narx bering",
                null
        );

        String createResp = mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialInquiry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentOffer.offerNumber").value(1))
                .andExpect(jsonPath("$.currentOffer.unitPrice").isEmpty())
                .andReturn().getResponse().getContentAsString();

        String inquiryId = objectMapper.readTree(createResp).get("id").asText();

        // 2. Buyer cannot accept own unpriced offer -> 409 Conflict
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isConflict());

        // 3. Buyer cannot counter out of turn -> 409 Conflict (turn is with SELLER)
        SubmitOfferRequest buyerOutOfTurn = new SubmitOfferRequest(
                new BigDecimal("1500.000"),
                new BigDecimal("10000.00"),
                "UZS",
                "FCA",
                "Navbatsiz taklif",
                null
        );
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/offers", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyerOutOfTurn)))
                .andExpect(status().isConflict());

        // 4. Seller cannot accept unpriced offer -> 409 Conflict
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict());

        // 5. Seller counters with Offer #2 (11,800 UZS) -> 201 Created
        SubmitOfferRequest sellerCounter = new SubmitOfferRequest(
                new BigDecimal("1500.000"),
                new BigDecimal("11800.00"),
                "UZS",
                "FCA (Samarqand)",
                "1500 kg uchun 11,800 soʻmdan beramiz",
                Instant.now().plusSeconds(172800)
        );

        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/offers", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .header("Idempotency-Key", "idemp-offer-seller-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerCounter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentOffer.offerNumber").value(2))
                .andExpect(jsonPath("$.currentOffer.proposerRole").value("SELLER"))
                .andExpect(jsonPath("$.currentOffer.unitPrice").value(11800.00))
                .andExpect(jsonPath("$.currentOffer.totalPrice").value(17700000.00))
                .andExpect(jsonPath("$.offers", hasSize(2)))
                .andExpect(jsonPath("$.offers[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.offers[1].status").value("PROPOSED"));

        // 6. Seller counteroffer idempotent replay -> 200 OK
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/offers", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .header("Idempotency-Key", "idemp-offer-seller-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerCounter)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "IDEMPOTENT_HIT"));

        // 7. Seller cannot accept own proposal #2 -> 409 Conflict
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict());

        // 8. Outsider cannot counter or accept -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // 9. Buyer counters with Offer #3 (11,200 UZS) -> 201 Created
        SubmitOfferRequest buyerCounter = new SubmitOfferRequest(
                new BigDecimal("1500.000"),
                new BigDecimal("11200.00"),
                "UZS",
                "FCA (Samarqand)",
                "11,200 soʻm boʻlsa bugunoq shartnoma qilamiz",
                Instant.now().plusSeconds(86400)
        );

        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/offers", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyerCounter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentOffer.offerNumber").value(3))
                .andExpect(jsonPath("$.currentOffer.proposerRole").value("BUYER"))
                .andExpect(jsonPath("$.currentOffer.unitPrice").value(11200.00))
                .andExpect(jsonPath("$.offers", hasSize(3)));

        // 10. Seller accepts Buyer's Offer #3 -> 200 OK and status AGREED
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGREED"))
                .andExpect(jsonPath("$.agreedOffer.offerNumber").value(3))
                .andExpect(jsonPath("$.agreedOffer.quantity").value(1500.000))
                .andExpect(jsonPath("$.agreedOffer.unitPrice").value(11200.00))
                .andExpect(jsonPath("$.agreedOffer.currency").value("UZS"))
                .andExpect(jsonPath("$.agreedOffer.deliveryTerms").value("FCA (Samarqand)"))
                .andExpect(jsonPath("$.agreedAt").isNotEmpty())
                .andExpect(jsonPath("$.currentOffer.status").value("ACCEPTED"));

        // 11. Cannot counter or accept once AGREED -> 409 Conflict
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/accept", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Buyer withdraws inquiry -> 200 OK and status WITHDRAWN")
    void withdrawInquiry_asBuyer_success() throws Exception {
        CreateInquiryRequest req = new CreateInquiryRequest(
                new BigDecimal("800.000"),
                "KG",
                new BigDecimal("11000.00"),
                "UZS",
                "EXW",
                "Tezkor xarid",
                null
        );

        String createResp = mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inquiryId = objectMapper.readTree(createResp).get("id").asText();

        // Seller tries to withdraw -> 403 Forbidden
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/withdraw", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());

        // Buyer withdraws -> 200 OK
        InquiryDecisionRequest decision = new InquiryDecisionRequest("Rejalar oʻzgardi");
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/withdraw", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.rejectionReason").value("Rejalar oʻzgardi"));
    }

    @Test
    @DisplayName("Seller rejects inquiry -> 200 OK and status REJECTED")
    void rejectInquiry_asSeller_success() throws Exception {
        CreateInquiryRequest req = new CreateInquiryRequest(
                new BigDecimal("700.000"),
                "KG",
                new BigDecimal("10500.00"),
                "UZS",
                "EXW",
                "Xarid soʻrovi",
                null
        );

        String createResp = mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inquiryId = objectMapper.readTree(createResp).get("id").asText();

        InquiryDecisionRequest decision = new InquiryDecisionRequest("Hozirda bu hajmda sotilmaydi");
        mockMvc.perform(post("/api/v1/market/inquiries/{inquiryId}/reject", inquiryId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Hozirda bu hajmda sotilmaydi"))
                .andExpect(jsonPath("$.currentOffer.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Inquiry detail view: participant authorized, outsider 403 Forbidden, farm internals not leaked")
    void inquiryDetail_authorizationAndPrivacy() throws Exception {
        CreateInquiryRequest req = new CreateInquiryRequest(
                new BigDecimal("600.000"),
                "KG",
                new BigDecimal("11500.00"),
                "UZS",
                "EXW",
                "Maxfiylik tekshiruvi",
                null
        );

        String createResp = mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inquiryId = objectMapper.readTree(createResp).get("id").asText();

        // 1. Outsider denied -> 403 Forbidden
        mockMvc.perform(get("/api/v1/market/inquiries/{inquiryId}", inquiryId)
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());

        // 2. Participant permitted -> 200 OK
        String detailContent = mockMvc.perform(get("/api/v1/market/inquiries/{inquiryId}", inquiryId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inquiryId))
                .andReturn().getResponse().getContentAsString();

        // 3. Confirm no private field coordinates, chemical treatments or warehouse notes exist
        org.assertj.core.api.Assertions.assertThat(detailContent)
                .doesNotContain("coordinates")
                .doesNotContain("Maxfiy oʻgʻitlar")
                .doesNotContain("Sovutgichli ombor №2");
    }

    @Test
    @DisplayName("List inquiries: role and status filtering, deterministic pagination")
    void listMyInquiries_filteringAndPagination() throws Exception {
        // Buyer creates 2 inquiries
        CreateInquiryRequest req1 = new CreateInquiryRequest(
                new BigDecimal("600.000"), "KG", new BigDecimal("11500.00"), "UZS", "EXW", "Inquiry 1", null
        );
        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        CreateInquiryRequest req2 = new CreateInquiryRequest(
                new BigDecimal("700.000"), "KG", new BigDecimal("11200.00"), "UZS", "EXW", "Inquiry 2", null
        );
        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Buyer lists inquiries as BUYER
        mockMvc.perform(get("/api/v1/market/inquiries")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("role", "BUYER")
                        .param("status", "OPEN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].buyerUserId").value(buyerUser.getId().toString()));

        // Seller lists inquiries as SELLER
        mockMvc.perform(get("/api/v1/market/inquiries")
                        .header("Authorization", "Bearer " + sellerToken)
                        .param("role", "SELLER")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sellerUserId").value(sellerUser.getId().toString()));

        // Outsider sees empty list
        mockMvc.perform(get("/api/v1/market/inquiries")
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Unauthenticated requests fail with 401 Unauthorized")
    void unauthenticatedAccess_failsWith401() throws Exception {
        mockMvc.perform(get("/api/v1/market/inquiries"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/market/harvest-listings/{listingId}/inquiries", rfqListing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
