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
import uz.hosil.platform.identity.domain.CarrierProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.CarrierProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.logistics.domain.ShipmentRequestEntity;
import uz.hosil.platform.logistics.repository.ShipmentRequestRepository;
import uz.hosil.platform.logistics.repository.ShipmentStatusHistoryRepository;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogisticsShipmentRequestIntegrationTests {

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
    private CarrierProfileRepository carrierProfileRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ShipmentRequestRepository shipmentRequestRepository;

    @Autowired
    private ShipmentStatusHistoryRepository shipmentStatusHistoryRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private HarvestListingRepository harvestListingRepository;

    private UserEntity buyerUser;
    private UserEntity sellerUser;
    private UserEntity carrierUser;
    private UserEntity carrierUserB;
    private UserEntity unverifiedCarrierUser;
    private UserEntity thirdPartyUser;
    private UserEntity adminUser;

    private String buyerToken;
    private String sellerToken;
    private String carrierToken;
    private String carrierTokenB;
    private String unverifiedCarrierToken;
    private String thirdPartyToken;
    private String adminToken;

    private OrderEntity confirmedOrder;

    @BeforeEach
    void setUp() {
        shipmentStatusHistoryRepository.deleteAll();
        shipmentRequestRepository.deleteAll();
        carrierProfileRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        harvestListingRepository.deleteAll();
        harvestBatchRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Users
        buyerUser = userRepository.save(new UserEntity("+998901110001", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyerUser.getId(), "Botir Xaridor", "Toshkent shahri", "Yunusobod"));
        buyerToken = jwtTokenProvider.generateAccessToken(buyerUser.getId(), buyerUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        sellerUser = userRepository.save(new UserEntity("+998901110002", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(sellerUser.getId(), "Salim Sotuvchi", "Samarqand viloyati", "Jomboy"));
        sellerToken = jwtTokenProvider.generateAccessToken(sellerUser.getId(), sellerUser.getPhoneNumber(), List.of("REGISTERED_USER", "SELLER"));

        carrierUser = userRepository.save(new UserEntity("+998901110003", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(carrierUser.getId(), "Komil Haydovchi", "Toshkent viloyati", "Chinoz"));
        carrierToken = jwtTokenProvider.generateAccessToken(carrierUser.getId(), carrierUser.getPhoneNumber(), List.of("REGISTERED_USER", "CARRIER"));

        carrierUserB = userRepository.save(new UserEntity("+998901110006", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(carrierUserB.getId(), "Bahrom Haydovchi", "Samarqand viloyati", "Urgut"));
        carrierTokenB = jwtTokenProvider.generateAccessToken(carrierUserB.getId(), carrierUserB.getPhoneNumber(), List.of("REGISTERED_USER", "CARRIER"));

        unverifiedCarrierUser = userRepository.save(new UserEntity("+998901110004", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(unverifiedCarrierUser.getId(), "Sherzod Noverified", "Buxoro viloyati", "Gʻijduvon"));
        unverifiedCarrierToken = jwtTokenProvider.generateAccessToken(unverifiedCarrierUser.getId(), unverifiedCarrierUser.getPhoneNumber(), List.of("REGISTERED_USER", "CARRIER"));

        thirdPartyUser = userRepository.save(new UserEntity("+998901110005", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(thirdPartyUser.getId(), "Begona Foydalanuvchi", "Fargʻona viloyati", "Quva"));
        thirdPartyToken = jwtTokenProvider.generateAccessToken(thirdPartyUser.getId(), thirdPartyUser.getPhoneNumber(), List.of("REGISTERED_USER"));

        adminUser = userRepository.save(new UserEntity("+998901110099", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(adminUser.getId(), "Admin Foydalanuvchi", "Toshkent shahri", "Mirzo Ulugʻbek"));
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getPhoneNumber(), List.of("REGISTERED_USER", "ADMINISTRATOR"));

        // 2. Carrier Profiles
        carrierProfileRepository.save(new CarrierProfileEntity(
                UUID.randomUUID(),
                carrierUser.getId(),
                "Hosil Express Logistics MCHJ",
                "LIC-2026-9988",
                "VERIFIED",
                Instant.now()
        ));

        carrierProfileRepository.save(new CarrierProfileEntity(
                UUID.randomUUID(),
                carrierUserB.getId(),
                "Sharq Trans Service MCHJ",
                "LIC-2026-7711",
                "VERIFIED",
                Instant.now()
        ));

        carrierProfileRepository.save(new CarrierProfileEntity(
                UUID.randomUUID(),
                unverifiedCarrierUser.getId(),
                "Chala Transport XK",
                "LIC-PENDING-001",
                "PENDING",
                null
        ));

        // 3. Baseline Confirmed Order with Order Items
        confirmedOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-20261017-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("25000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent sh., Yunusobod 4-mavze, 12-uy",
                "FCA Samarqand Farm Gate",
                "Wholesale order ready for shipment",
                Instant.now().plus(24, ChronoUnit.HOURS),
                "order-idemp-001"
        );
        confirmedOrder.setConfirmedAt(Instant.now());
        confirmedOrder = orderRepository.save(confirmedOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                confirmedOrder.getId(),
                UUID.randomUUID(),
                "Pomidor Elita Qizil",
                "Pomidor",
                new BigDecimal("5000.000"),
                "KG",
                new BigDecimal("5000.00"),
                new BigDecimal("25000000.00"),
                "UZS"
        ));
    }

    @Test
    @DisplayName("Requester creates shipment request linked to confirmed order; inherits cargo defaults and creates REQUESTED status")
    void createShipmentRequest_Success_WithLinkedOrder() throws Exception {
        Instant now = Instant.now();
        Instant windowStart = now.plus(1, ChronoUnit.DAYS);
        Instant windowEnd = now.plus(2, ChronoUnit.DAYS);

        CreateShipmentRequest request = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                null, // will default from order
                null, // will default from order
                new BigDecimal("12.50"),
                "AGRICULTURAL_PRODUCE",
                "REFRIGERATED_TRUCK",
                "Samarqand viloyati",
                "Jomboy",
                "Jomboy t., Dehqonobod MFY, 45-dala",
                "Salim Sotuvchi",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                null, // will default to order deliveryAddress
                "Botir Xaridor",
                "+998901110001",
                windowStart,
                windowEnd,
                now.plus(3, ChronoUnit.DAYS),
                "Handle refrigerated produce with care",
                "req-idemp-001"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.requestNumber", startsWith("SR-")))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.cargoDescription").value("Pomidor Elita Qizil"))
                .andExpect(jsonPath("$.cargoWeightKg").value(5000.00))
                .andExpect(jsonPath("$.destinationAddress").value("Toshkent sh., Yunusobod 4-mavze, 12-uy"))
                .andExpect(jsonPath("$.assignedCarrierUserId").doesNotExist())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Verify audit status history created
        mockMvc.perform(get("/api/v1/logistics/shipment-requests/" + dto.id() + "/history")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].toStatus").value("REQUESTED"))
                .andExpect(jsonPath("$[0].actorRole").value("REQUESTER"));
    }

    @Test
    @DisplayName("Attempting to create duplicate active shipment request on the same order fails with 409 Conflict")
    void createShipmentRequest_DuplicateActiveOnSameOrder_FailsConflict() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req1 = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "first-req-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Attempt second active request for same order
        CreateShipmentRequest req2 = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor duplicate",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                null,
                null,
                null,
                "Toshkent shahri",
                "Yunusobod",
                null,
                null,
                null,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "second-req-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("active shipment request already exists")));
    }

    @Test
    @DisplayName("Outsider user attempting to request shipment for an order they do not participate in fails with 403 Forbidden")
    void createShipmentRequest_OutsiderUser_FailsForbidden() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                null,
                null,
                null,
                "Toshkent shahri",
                "Yunusobod",
                null,
                null,
                null,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "outsider-req"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + thirdPartyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Attempting to request shipment for an unconfirmed order (AWAITING_SELLER_CONFIRMATION) fails with 409 Conflict")
    void createShipmentRequest_IneligibleOrderStatus_FailsConflict() throws Exception {
        OrderEntity unconfirmed = new OrderEntity(
                UUID.randomUUID(),
                "ORD-UNCONFIRMED-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("10000000.00"),
                "UZS",
                "AWAITING_SELLER_CONFIRMATION",
                "BUYER_ARRANGES",
                "Toshkent",
                "Standard",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "unconfirmed-key"
        );
        unconfirmed = orderRepository.save(unconfirmed);

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                unconfirmed.getId(),
                null,
                "Piyoz",
                new BigDecimal("2000.00"),
                null,
                null,
                null,
                "Jizzax viloyati",
                "Gʻallaorol",
                null,
                null,
                null,
                "Toshkent shahri",
                "Chilonzor",
                null,
                null,
                null,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "ineligible-req"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("not eligible for transport request")));
    }

    @Test
    @DisplayName("Idempotency: Re-submitting the same shipment request returns existing record without creating duplicates")
    void createShipmentRequest_Idempotency() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand Street 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Yunusobod 4-12",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "idemp-fixed-key-01"
        );

        MvcResult res1 = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto1 = objectMapper.readValue(res1.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        MvcResult res2 = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto2 = objectMapper.readValue(res2.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        assertEquals(dto1.id(), dto2.id());
        assertEquals(dto1.requestNumber(), dto2.requestNumber());
        assertEquals(1, shipmentRequestRepository.count());
    }

    @Test
    @DisplayName("Location Privacy: Unassigned carrier views open request with masked addresses; Requester views unmasked details")
    void getShipmentRequest_PrivacyMasking() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Maxfiy koʻcha 77-uy",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Maxfiy omborxona 12",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "privacy-test-key"
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto createdDto = objectMapper.readValue(createResult.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // 1. Unassigned carrier views request -> addresses & phones masked
        mockMvc.perform(get("/api/v1/logistics/shipment-requests/" + createdDto.id())
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originRegion").value("Samarqand viloyati"))
                .andExpect(jsonPath("$.originDistrict").value("Jomboy"))
                .andExpect(jsonPath("$.originAddress").value("[HIDDEN_UNTIL_ASSIGNED]"))
                .andExpect(jsonPath("$.originContactPhone").value("[HIDDEN_UNTIL_ASSIGNED]"))
                .andExpect(jsonPath("$.destinationRegion").value("Toshkent shahri"))
                .andExpect(jsonPath("$.destinationDistrict").value("Yunusobod"))
                .andExpect(jsonPath("$.destinationAddress").value("[HIDDEN_UNTIL_ASSIGNED]"))
                .andExpect(jsonPath("$.destinationContactPhone").value("[HIDDEN_UNTIL_ASSIGNED]"));

        // 2. Requester views request -> full unmasked details visible
        mockMvc.perform(get("/api/v1/logistics/shipment-requests/" + createdDto.id())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originAddress").value("Maxfiy koʻcha 77-uy"))
                .andExpect(jsonPath("$.originContactPhone").value("+998901110002"))
                .andExpect(jsonPath("$.destinationAddress").value("Maxfiy omborxona 12"))
                .andExpect(jsonPath("$.destinationContactPhone").value("+998901110001"));
    }

    @Test
    @DisplayName("Carrier Acceptance: Unverified carrier fails with 409; Verified carrier succeeds and unmasks location")
    void acceptShipmentRequest_VerificationAndAssignment() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "carrier-assign-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // 1. Unverified carrier attempts to accept -> 409 Conflict
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + unverifiedCarrierToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("only VERIFIED carriers can accept")));

        // 2. Verified carrier accepts -> 200 OK, status ACCEPTED, assignedCarrierUserId set
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.assignedCarrierUserId").value(carrierUser.getId().toString()))
                .andExpect(jsonPath("$.originAddress").value("Samarqand koʻcha 1"))
                .andExpect(jsonPath("$.destinationAddress").value("Toshkent koʻcha 2"));

        // 3. Second carrier attempts to accept already accepted request -> 409 Conflict
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cannot be accepted")));
    }

    @Test
    @DisplayName("Status Progression: Assigned carrier executes sequential transit updates (ACCEPTED -> PICKUP_ARRANGED -> PICKED_UP -> IN_TRANSIT -> DELIVERED)")
    void updateShipmentStatus_SequentialTransitProgression() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                null, // Standalone transport request (not linked to marketplace order)
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "transit-progression-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier accepts
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        // Step 1: Invalid skip directly to DELIVERED -> fails 409 Conflict
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("DELIVERED", "Skipping steps"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Invalid shipment status transition")));

        // Step 2: Valid progression: ACCEPTED -> PICKUP_ARRANGED
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Pickup scheduled for tomorrow 10:00 AM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKUP_ARRANGED"));

        // Step 3: PICKUP_ARRANGED -> PICKED_UP
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "Cargo loaded on truck"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Step 4: PICKED_UP -> IN_TRANSIT
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("IN_TRANSIT", "En route to Tashkent warehouse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        // Step 5: IN_TRANSIT -> DELIVERED
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("DELIVERED", "Delivered and signed by recipient"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Step 6: Verify full audit status history has all 6 status changes
        mockMvc.perform(get("/api/v1/logistics/shipment-requests/" + dto.id() + "/history")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].toStatus").value("REQUESTED"))
                .andExpect(jsonPath("$[1].toStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$[2].toStatus").value("PICKUP_ARRANGED"))
                .andExpect(jsonPath("$[3].toStatus").value("PICKED_UP"))
                .andExpect(jsonPath("$[4].toStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$[5].toStatus").value("DELIVERED"));
    }


    @Test
    @DisplayName("Cancellation: Permitted prior to cargo pickup (REQUESTED, ACCEPTED, PICKUP_ARRANGED); Blocked once PICKED_UP or IN_TRANSIT")
    void cancelShipmentRequest_CancellationGuards() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                null, // Standalone transport request
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "cancel-guard-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier accepts and marks PICKUP_ARRANGED -> then PICKED_UP
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Arranged"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "Loaded"))))
                .andExpect(status().isOk());

        // Now requester tries to cancel after cargo is picked up -> 409 Conflict
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelShipmentRequest("Changed my mind after truck departed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cargo is already loaded/in-transit")));
    }

    @Test
    @DisplayName("Mixed Units: Order with multiple incompatible units cannot auto-derive weight; requires explicit cargoWeightKg")
    void createShipmentRequest_MixedUnitsOrder_WithoutExplicitWeight_FailsConflict() throws Exception {
        OrderEntity mixedOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-MIXED-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "DIRECT_INPUT_ORDER",
                new BigDecimal("15000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "mixed-ord-idemp"
        );
        mixedOrder = orderRepository.save(mixedOrder);

        // Item 1: KG
        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                mixedOrder.getId(),
                null,
                "NPK Oʻgʻit",
                null,
                new BigDecimal("100.000"),
                "KG",
                new BigDecimal("50000.00"),
                new BigDecimal("5000000.00"),
                "UZS"
        ));

        // Item 2: LITER
        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                mixedOrder.getId(),
                null,
                "Suyuq Biogumus",
                null,
                new BigDecimal("200.000"),
                "LITER",
                new BigDecimal("50000.00"),
                new BigDecimal("10000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                mixedOrder.getId(),
                null,
                null,
                null, // null weight -> should fail because of mixed units
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Omborxona",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Fermer ombori",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "mixed-units-req-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Order contains items with mixed units")));
    }

    @Test
    @DisplayName("Non-Mass Unit: Order with non-mass unit (LITER) without explicit cargoWeightKg fails with 409 Conflict")
    void createShipmentRequest_NonMassUnitOrder_WithoutExplicitWeight_FailsConflict() throws Exception {
        OrderEntity literOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-LITER-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "DIRECT_INPUT_ORDER",
                new BigDecimal("8000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "liter-ord-idemp"
        );
        literOrder = orderRepository.save(literOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                literOrder.getId(),
                null,
                "Suyuq Oʻgʻit Super-Gro",
                null,
                new BigDecimal("500.000"),
                "LITER",
                new BigDecimal("16000.00"),
                new BigDecimal("8000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                literOrder.getId(),
                null,
                null,
                null, // null weight -> cannot derive kg from liter
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Kimyo ombori",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Fermer ombori",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "liter-units-fail-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cannot be derived automatically from order units (LITER)")));
    }

    @Test
    @DisplayName("Non-Mass Unit: Order with non-mass unit (LITER) succeeds when explicit cargoWeightKg is provided")
    void createShipmentRequest_NonMassUnitOrder_WithExplicitWeight_Succeeds() throws Exception {
        OrderEntity literOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-LITER-002",
                buyerUser.getId(),
                sellerUser.getId(),
                "DIRECT_INPUT_ORDER",
                new BigDecimal("8000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "liter-ord-idemp-02"
        );
        literOrder = orderRepository.save(literOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                literOrder.getId(),
                null,
                "Suyuq Oʻgʻit Super-Gro",
                null,
                new BigDecimal("500.000"),
                "LITER",
                new BigDecimal("16000.00"),
                new BigDecimal("8000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                literOrder.getId(),
                null,
                null,
                new BigDecimal("600.00"), // Explicit weight supplied
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Kimyo ombori",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Fermer ombori",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "liter-units-success-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.cargoWeightKg").value(600.00))
                .andExpect(jsonPath("$.cargoDescription").value("Suyuq Oʻgʻit Super-Gro"));
    }

    @Test
    @DisplayName("Standalone Shipment: Request without linked order or harvest batch succeeds with explicit cargo and route details")
    void createShipmentRequest_Standalone_SucceedsWithExplicitDetails() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest standaloneReq = new CreateShipmentRequest(
                null,
                null,
                "Qurilish gʻishtlari va materiallar",
                new BigDecimal("12000.00"),
                new BigDecimal("25.00"),
                "CONSTRUCTION_MATERIAL",
                "FLATBED_TRUCK",
                "Toshkent viloyati",
                "Chinoz",
                "Chinoz gʻisht zavodi",
                "Farhod Usta",
                "+998901110055",
                "Sirdaryo viloyati",
                "Sayxunobod",
                "Sayxunobod qurilish uchastkasi",
                "Anvar Muhandis",
                "+998901110066",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                now.plus(3, ChronoUnit.DAYS),
                "Ehtiyotkorlik bilan tushirilsin",
                "standalone-req-001"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(standaloneReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.orderId").doesNotExist())
                .andExpect(jsonPath("$.harvestBatchId").doesNotExist())
                .andExpect(jsonPath("$.cargoDescription").value("Qurilish gʻishtlari va materiallar"))
                .andExpect(jsonPath("$.cargoWeightKg").value(12000.00));
    }

    @Test
    @DisplayName("Linkage Integrity: Mismatched harvest batch linked to wholesale order fails with 409 Conflict")
    void createShipmentRequest_WholesaleOrder_BackingBatchMismatch_FailsConflict() throws Exception {
        FarmEntity farm = farmRepository.save(new FarmEntity(
                sellerUser.getId(),
                "Salim Bogʻi",
                "Samarqand viloyati",
                "Jomboy",
                new BigDecimal("10.00"),
                "FARM_ENTERPRISE"
        ));

        HarvestBatchEntity batch1 = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Olma",
                "1-nav",
                new BigDecimal("1000.000"),
                new BigDecimal("1000.000"),
                "KG",
                LocalDate.now(),
                "Ombor 1",
                "RECORDED",
                "BATCH-01",
                "idemp-b1",
                1L
        ));

        HarvestBatchEntity batch2 = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Olma",
                "1-nav",
                new BigDecimal("1000.000"),
                new BigDecimal("1000.000"),
                "KG",
                LocalDate.now(),
                "Ombor 2",
                "RECORDED",
                "BATCH-02",
                "idemp-b2",
                1L
        ));

        HarvestListingEntity listing1 = harvestListingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch1.getId(),
                UUID.randomUUID(),
                "Olma Bogʻi",
                "Olma tavsifi",
                "Olma",
                "1-nav",
                new BigDecimal("15000.00"),
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
                "Samarqand viloyati",
                "Jomboy",
                "PUBLISHED",
                "idemp-l1",
                Instant.now()
        ));

        OrderEntity wholesaleOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-WHOLESALE-01",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("7500000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "idemp-ord-w1"
        );
        wholesaleOrder.setHarvestListingId(listing1.getId());
        wholesaleOrder.setConfirmedAt(Instant.now());
        wholesaleOrder = orderRepository.save(wholesaleOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                wholesaleOrder.getId(),
                listing1.getId(),
                "Olma 1-nav",
                "Olma",
                new BigDecimal("500.000"),
                "KG",
                new BigDecimal("15000.00"),
                new BigDecimal("7500000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        // Requester links wholesaleOrder but specifies batch2 instead of batch1
        CreateShipmentRequest mismatchedReq = new CreateShipmentRequest(
                wholesaleOrder.getId(),
                batch2.getId(), // MISMATCH
                null,
                null,
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Omborxona",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Bozor",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "mismatched-batch-req"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchedReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("does not match order backing batch")));
    }

    @Test
    @DisplayName("Harvest Batch: Duplicate active shipment request on same harvest batch fails with 409 Conflict")
    void createShipmentRequest_DuplicateActiveOnHarvestBatch_FailsConflict() throws Exception {
        FarmEntity farm = farmRepository.save(new FarmEntity(
                sellerUser.getId(),
                "Salim Bogʻi",
                "Samarqand viloyati",
                "Jomboy",
                new BigDecimal("10.00"),
                "FARM_ENTERPRISE"
        ));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Nok",
                "Oʻrmon goʻzali",
                new BigDecimal("3000.000"),
                new BigDecimal("3000.000"),
                "KG",
                LocalDate.now(),
                "Dala ombori",
                "RECORDED",
                "BATCH-NOK-01",
                "idemp-nok-01",
                1L
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req1 = new CreateShipmentRequest(
                null,
                batch.getId(),
                null,
                null, // will derive from batch (3000 kg)
                null,
                null,
                null,
                null,
                null,
                "Dala ombori 5",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Bektemir",
                "Meva bazasi 11",
                "Meva Savdo MCHJ",
                "+998901110077",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "batch-req-01"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cargoWeightKg").value(3000.00));

        // Attempt second active request for the same batch
        CreateShipmentRequest req2 = new CreateShipmentRequest(
                null,
                batch.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Dala ombori 5",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Bektemir",
                "Meva bazasi 11",
                "Meva Savdo MCHJ",
                "+998901110077",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "batch-req-02"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("active shipment request already exists for harvest batch")));
    }

    @Test
    @DisplayName("Idempotency: Re-submitting same key with mismatched payload fails with 409 Conflict")
    void createShipmentRequest_IdempotencyMismatch_FailsConflict() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req1 = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "mismatch-idemp-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Same key, but mismatched cargoWeightKg
        CreateShipmentRequest req2 = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("3000.00"), // MISMATCH
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "mismatch-idemp-key"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Idempotency key reuse with mismatched cargoWeightKg")));
    }

    @Test
    @DisplayName("Payment Boundary: Moving shipment to PICKED_UP for an order in PENDING_PAYMENT status fails with 409 Conflict")
    void updateShipmentStatus_PendingPaymentOrder_BlocksPickup_FailsConflict() throws Exception {
        OrderEntity pendingPaymentOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-PEND-PAY-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("10000000.00"),
                "UZS",
                "PENDING_PAYMENT",
                "BUYER_ARRANGES",
                "Toshkent",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "pend-pay-order-key"
        );
        pendingPaymentOrder = orderRepository.save(pendingPaymentOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                pendingPaymentOrder.getId(),
                null,
                "Sabzi",
                "Sabzi",
                new BigDecimal("2000.000"),
                "KG",
                new BigDecimal("5000.00"),
                new BigDecimal("10000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                pendingPaymentOrder.getId(),
                null,
                "Sabzi",
                new BigDecimal("2000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Ombor",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Ombor",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "pend-pay-shipment-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier accepts
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        // Carrier transitions to PICKUP_ARRANGED
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Arrived at farm"))))
                .andExpect(status().isOk());

        // Carrier attempts to transition to PICKED_UP while order is still PENDING_PAYMENT -> fails with 409 Conflict
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "Loading cargo"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cannot dispatch cargo (PICKED_UP) for order ORD-PEND-PAY-001 with payment pending")));
    }

    @Test
    @DisplayName("Dispatch Authorization: Moving shipment to PICKED_UP for a CONFIRMED wholesale order fails closed with 409 Conflict pending DEC-007")
    void updateShipmentStatus_ConfirmedWholesaleOrder_BlocksPickup_FailsConflict() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Ombor",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Bozor",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "confirmed-order-dispatch-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier accepts
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        // Carrier arranges pickup
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Arrived at origin"))))
                .andExpect(status().isOk());

        // Carrier attempts to transition to PICKED_UP -> fails closed with 409 Conflict
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "Loading wholesale goods"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("which does not constitute verified financial clearance")))
                .andExpect(jsonPath("$.message", containsString("Physical dispatch authorization is blocked pending resolution of payment settlement (DEC-007)")));
    }

    @Test
    @DisplayName("Dispatch Authorization: Moving shipment to PICKED_UP for a CANCELLED order fails with 409 Conflict")
    void updateShipmentStatus_CancelledOrder_BlocksPickup_FailsConflict() throws Exception {
        OrderEntity cancelledOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-CANCELLED-001",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("5000000.00"),
                "UZS",
                "CONFIRMED", // Initially confirmed for shipment creation
                "BUYER_ARRANGES",
                "Toshkent",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "cancelled-order-key"
        );
        cancelledOrder = orderRepository.save(cancelledOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                cancelledOrder.getId(),
                null,
                "Bodring",
                "Bodring",
                new BigDecimal("1000.000"),
                "KG",
                new BigDecimal("5000.00"),
                new BigDecimal("5000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                cancelledOrder.getId(),
                null,
                "Bodring",
                new BigDecimal("1000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Ombor",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Ombor",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "cancelled-shipment-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier accepts and arranges pickup
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Scheduled"))))
                .andExpect(status().isOk());

        // Order is now cancelled in commerce system
        cancelledOrder.setStatus("CANCELLED");
        orderRepository.save(cancelledOrder);

        // Carrier attempts to transition to PICKED_UP -> fails with 409 Conflict
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "Attempting pickup"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cannot dispatch cargo (PICKED_UP) for cancelled order ORD-CANCELLED-001")));
    }

    @Test
    @DisplayName("Carrier Isolation: Unassigned carrier attempting status transition fails with 403 Forbidden")
    void updateShipmentStatus_UnauthorizedCarrier_FailsForbidden() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "unauth-carrier-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Carrier A accepts
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        // Carrier B attempts to update status on Carrier A's shipment -> fails 403 Forbidden
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "Intruding update"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Terminal Immutability: Attempting to update shipment status from DELIVERED fails with 409 Conflict")
    void updateShipmentStatus_TerminalStateDelivered_Immutable() throws Exception {
        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                null, // Standalone transport request
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "terminal-immut-key"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // Advance to DELIVERED
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + carrierToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKUP_ARRANGED", "OK"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("PICKED_UP", "OK"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("IN_TRANSIT", "OK"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("DELIVERED", "Delivered successfully"))))
                .andExpect(status().isOk());

        // Attempt to mutate delivered shipment -> fails 409 Conflict
        mockMvc.perform(patch("/api/v1/logistics/shipment-requests/" + dto.id() + "/status")
                        .header("Authorization", "Bearer " + carrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest("IN_TRANSIT", "Reopen delivery"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("terminal status DELIVERED")));
    }

    @Test
    @DisplayName("Carrier Onboarding: Self-service PUT creates PENDING status; cannot accept shipments until Administrator verifies")
    void carrierProfile_SelfServiceRegistration_DefaultsToPending_AdminVerifies() throws Exception {
        UserEntity newCarrier = userRepository.save(new UserEntity("+998901110090", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(newCarrier.getId(), "Otabek Haydovchi", "Toshkent", "Chilanzar"));
        String newCarrierToken = jwtTokenProvider.generateAccessToken(newCarrier.getId(), newCarrier.getPhoneNumber(), List.of("REGISTERED_USER", "CARRIER"));

        CreateCarrierProfileRequest profileReq = new CreateCarrierProfileRequest(
                "Otabek Karvon Express MCHJ",
                "LIC-UZ-2026-9090"
        );

        // 1. Self-service PUT -> verificationStatus MUST default to PENDING
        mockMvc.perform(put("/api/v1/logistics/carrier-profile")
                        .header("Authorization", "Bearer " + newCarrierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist());

        // 2. Create open shipment request
        Instant now = Instant.now();
        CreateShipmentRequest shipReq = new CreateShipmentRequest(
                confirmedOrder.getId(),
                null,
                "Pomidor",
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Samarqand koʻcha 1",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Toshkent koʻcha 2",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "pending-carrier-accept-test"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ShipmentRequestDetailDto dto = objectMapper.readValue(createRes.getResponse().getContentAsString(), ShipmentRequestDetailDto.class);

        // 3. Pending carrier attempts to accept shipment -> fails 409 Conflict
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + newCarrierToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("only VERIFIED carriers can accept shipments")));

        // 4. Administrator verifies carrier profile via PATCH /api/v1/logistics/carrier-profile/{userId}/verification
        mockMvc.perform(patch("/api/v1/logistics/carrier-profile/" + newCarrier.getId() + "/verification")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCarrierVerificationRequest("VERIFIED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        // 5. Now verified carrier accepts shipment -> succeeds 200 OK
        mockMvc.perform(post("/api/v1/logistics/shipment-requests/" + dto.id() + "/accept")
                        .header("Authorization", "Bearer " + newCarrierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.assignedCarrierUserId").value(newCarrier.getId().toString()));
    }

    @Test
    @DisplayName("Wholesale Buyer: Can create shipment request linking both confirmed order and matching harvest batch without being farm owner")
    void createShipmentRequest_WholesaleBuyer_WithMatchingOrderAndBatch_Succeeds() throws Exception {
        FarmEntity farm = farmRepository.save(new FarmEntity(
                sellerUser.getId(),
                "Salim Bogʻi 2",
                "Samarqand viloyati",
                "Jomboy",
                new BigDecimal("15.00"),
                "FARM_ENTERPRISE"
        ));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Uzum",
                "Kishmish",
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"),
                "KG",
                LocalDate.now(),
                "Omborxona 1",
                "RECORDED",
                "BATCH-UZUM-01",
                "idemp-uzum-1",
                1L
        ));

        HarvestListingEntity listing = harvestListingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                UUID.randomUUID(),
                "Kishmish Uzumi",
                "Shirin uzum",
                "Uzum",
                "Kishmish",
                new BigDecimal("20000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("100.000"),
                new BigDecimal("10.000"),
                "Samarqand viloyati",
                "Jomboy",
                "PUBLISHED",
                "idemp-list-uzum-1",
                Instant.now()
        ));

        OrderEntity wholesaleOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-BUYER-BATCH-01",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("10000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "idemp-ord-buyer-b1"
        );
        wholesaleOrder.setHarvestListingId(listing.getId());
        wholesaleOrder.setConfirmedAt(Instant.now());
        wholesaleOrder = orderRepository.save(wholesaleOrder);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                wholesaleOrder.getId(),
                listing.getId(),
                "Kishmish Uzum",
                "Uzum",
                new BigDecimal("500.000"),
                "KG",
                new BigDecimal("20000.00"),
                new BigDecimal("10000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();
        CreateShipmentRequest req = new CreateShipmentRequest(
                wholesaleOrder.getId(),
                batch.getId(),
                null,
                null,
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala ombori",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Bozor",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "buyer-linked-order-batch-req"
        );

        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(wholesaleOrder.getId().toString()))
                .andExpect(jsonPath("$.harvestBatchId").value(batch.getId().toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("Harvest Batch Cardinality: Multiple distinct wholesale orders backed by same batch can each have active shipments")
    void createShipmentRequest_MultipleOrdersFromSameHarvestBatch_BothSucceed() throws Exception {
        FarmEntity farm = farmRepository.save(new FarmEntity(
                sellerUser.getId(),
                "Salim Sabzavot",
                "Samarqand viloyati",
                "Jomboy",
                new BigDecimal("20.00"),
                "FARM_ENTERPRISE"
        ));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Sabzi",
                "Qizil",
                new BigDecimal("10000.000"),
                new BigDecimal("10000.000"),
                "KG",
                LocalDate.now(),
                "Sabzi Ombori",
                "RECORDED",
                "BATCH-SABZI-01",
                "idemp-sabzi-1",
                1L
        ));

        HarvestListingEntity listing = harvestListingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                UUID.randomUUID(),
                "Qizil Sabzi",
                "Shirin sabzi",
                "Sabzi",
                "Qizil",
                new BigDecimal("4000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("10000.000"),
                new BigDecimal("10000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("500.000"),
                new BigDecimal("50.000"),
                "Samarqand viloyati",
                "Jomboy",
                "PUBLISHED",
                "idemp-list-sabzi-1",
                Instant.now()
        ));

        // Order 1 for Buyer A
        OrderEntity order1 = new OrderEntity(
                UUID.randomUUID(),
                "ORD-SABZI-01",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("4000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "idemp-ord-sabzi-1"
        );
        order1.setHarvestListingId(listing.getId());
        order1.setConfirmedAt(Instant.now());
        order1 = orderRepository.save(order1);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                order1.getId(),
                listing.getId(),
                "Sabzi Qizil",
                "Sabzi",
                new BigDecimal("1000.000"),
                "KG",
                new BigDecimal("4000.00"),
                new BigDecimal("4000000.00"),
                "UZS"
        ));

        // Order 2 for Buyer B (thirdPartyUser)
        String buyerBToken = jwtTokenProvider.generateAccessToken(thirdPartyUser.getId(), thirdPartyUser.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));
        OrderEntity order2 = new OrderEntity(
                UUID.randomUUID(),
                "ORD-SABZI-02",
                thirdPartyUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("6000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Fargʻona shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "idemp-ord-sabzi-2"
        );
        order2.setHarvestListingId(listing.getId());
        order2.setConfirmedAt(Instant.now());
        order2 = orderRepository.save(order2);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                order2.getId(),
                listing.getId(),
                "Sabzi Qizil",
                "Sabzi",
                new BigDecimal("1500.000"),
                "KG",
                new BigDecimal("4000.00"),
                new BigDecimal("6000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();

        // 1. Shipment for Order 1
        CreateShipmentRequest req1 = new CreateShipmentRequest(
                order1.getId(),
                batch.getId(),
                null,
                null,
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala ombori",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Yunusobod",
                "Bozor 1",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "ship-order1-batch-req"
        );
        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(order1.getId().toString()))
                .andExpect(jsonPath("$.harvestBatchId").value(batch.getId().toString()));

        // 2. Shipment for Order 2 (same harvest batch)
        CreateShipmentRequest req2 = new CreateShipmentRequest(
                order2.getId(),
                batch.getId(),
                null,
                null,
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala ombori",
                "Salim",
                "+998901110002",
                "Fargʻona shahri",
                "Markaz",
                "Bozor 2",
                "Uchinchi Shaxs",
                "+998901110004",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "ship-order2-batch-req"
        );
        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(order2.getId().toString()))
                .andExpect(jsonPath("$.harvestBatchId").value(batch.getId().toString()));
    }

    @Test
    @DisplayName("Harvest Batch Cardinality: Farmer can create batch-only shipment alongside order shipment, but duplicate batch-only fails 409")
    void createShipmentRequest_BatchOnlyAllowedAlongsideOrderShipment_DuplicateBatchOnlyRejected() throws Exception {
        FarmEntity farm = farmRepository.save(new FarmEntity(
                sellerUser.getId(),
                "Salim Piyoz Farm",
                "Samarqand viloyati",
                "Jomboy",
                new BigDecimal("25.00"),
                "FARM_ENTERPRISE"
        ));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                null,
                "Piyoz",
                "Zafar",
                new BigDecimal("15000.000"),
                new BigDecimal("15000.000"),
                "KG",
                LocalDate.now(),
                "Dala cheti",
                "RECORDED",
                "BATCH-PIYOZ-01",
                "idemp-piyoz-1",
                1L
        ));

        HarvestListingEntity listing = harvestListingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                UUID.randomUUID(),
                "Zafar Piyoz",
                "Sariq piyoz",
                "Piyoz",
                "Zafar",
                new BigDecimal("3000.00"),
                "UZS",
                "FIXED",
                false,
                new BigDecimal("15000.000"),
                new BigDecimal("15000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("1000.000"),
                new BigDecimal("100.000"),
                "Samarqand viloyati",
                "Jomboy",
                "PUBLISHED",
                "idemp-list-piyoz-1",
                Instant.now()
        ));

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                "ORD-PIYOZ-01",
                buyerUser.getId(),
                sellerUser.getId(),
                "HARVEST_PURCHASE_REQUEST",
                new BigDecimal("9000000.00"),
                "UZS",
                "CONFIRMED",
                "BUYER_ARRANGES",
                "Toshkent shahri",
                "FCA Farm Gate",
                null,
                Instant.now().plus(24, ChronoUnit.HOURS),
                "idemp-ord-piyoz-1"
        );
        order.setHarvestListingId(listing.getId());
        order.setConfirmedAt(Instant.now());
        order = orderRepository.save(order);

        orderItemRepository.save(new OrderItemEntity(
                UUID.randomUUID(),
                order.getId(),
                listing.getId(),
                "Piyoz Zafar",
                "Piyoz",
                new BigDecimal("3000.000"),
                "KG",
                new BigDecimal("3000.00"),
                new BigDecimal("9000000.00"),
                "UZS"
        ));

        Instant now = Instant.now();

        // 1. Order shipment created by buyer
        CreateShipmentRequest orderShipReq = new CreateShipmentRequest(
                order.getId(),
                batch.getId(),
                null,
                null,
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala cheti",
                "Salim",
                "+998901110002",
                "Toshkent shahri",
                "Qoʻyliq",
                "Omborxona",
                "Botir",
                "+998901110001",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "order-piyoz-ship-req"
        );
        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderShipReq)))
                .andExpect(status().isCreated());

        // 2. Farmer creates batch-only transport request to move remainder to cold storage
        CreateShipmentRequest batchOnlyReq1 = new CreateShipmentRequest(
                null, // No order
                batch.getId(),
                null,
                new BigDecimal("12000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala cheti",
                "Salim",
                "+998901110002",
                "Samarqand viloyati",
                "Samarqand shahri",
                "Muzlatgich omborxona",
                "Salim",
                "+998901110002",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "batch-only-piyoz-req-1"
        );
        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchOnlyReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").doesNotExist())
                .andExpect(jsonPath("$.harvestBatchId").value(batch.getId().toString()));

        // 3. Farmer attempts second concurrent batch-only transport request for the same batch -> rejected with 409 Conflict
        CreateShipmentRequest batchOnlyReq2 = new CreateShipmentRequest(
                null,
                batch.getId(),
                null,
                new BigDecimal("5000.00"),
                null,
                null,
                null,
                "Samarqand viloyati",
                "Jomboy",
                "Dala cheti",
                "Salim",
                "+998901110002",
                "Samarqand viloyati",
                "Samarqand shahri",
                "Boshqa ombor",
                "Salim",
                "+998901110002",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS),
                null,
                null,
                "batch-only-piyoz-req-2"
        );
        mockMvc.perform(post("/api/v1/logistics/shipment-requests")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchOnlyReq2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("An active shipment request already exists for harvest batch")));
    }
}

