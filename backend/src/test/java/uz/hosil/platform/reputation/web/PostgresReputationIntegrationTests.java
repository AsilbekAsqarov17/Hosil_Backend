package uz.hosil.platform.reputation.web;

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
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.reputation.domain.ReviewEntity;
import uz.hosil.platform.reputation.domain.ReviewModerationLogEntity;
import uz.hosil.platform.reputation.domain.ReviewReportEntity;
import uz.hosil.platform.reputation.domain.ReviewResponseEntity;
import uz.hosil.platform.reputation.domain.ReviewStatus;
import uz.hosil.platform.reputation.domain.ReviewType;
import uz.hosil.platform.reputation.repository.ReviewModerationLogRepository;
import uz.hosil.platform.reputation.repository.ReviewReportRepository;
import uz.hosil.platform.reputation.repository.ReviewRepository;
import uz.hosil.platform.reputation.repository.ReviewResponseRepository;
import uz.hosil.platform.reputation.repository.UserReputationAggregateRepository;
import uz.hosil.platform.reputation.service.ReputationService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PostgreSQL 16 + Flyway backed integration tests validating the complete reputation lifecycle:
 * two-sided reviews, double-blind publishing window, 90-day repeat pair damping, factual rebuttals,
 * abuse reporting, and audited administrative moderation.
 * Conforms to docs/12_reputation_and_reviews.md, PRD REV-001..REV-010, and DEC-018.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PostgresReputationIntegrationTests {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewResponseRepository reviewResponseRepository;

    @Autowired
    private ReviewReportRepository reviewReportRepository;

    @Autowired
    private ReviewModerationLogRepository reviewModerationLogRepository;

    @Autowired
    private UserReputationAggregateRepository aggregateRepository;

    @Autowired
    private ReputationService reputationService;

    private UserEntity buyer;
    private String buyerToken;

    private UserEntity seller;
    private String sellerToken;

    private UserEntity admin;
    private String adminToken;

    private UserEntity bystander;
    private String bystanderToken;

    @BeforeEach
    void setUp() {
        reputationService.setClock(Clock.systemUTC());

        buyer = createTestUser("+99890123");
        buyerToken = jwtTokenProvider.generateAccessToken(buyer.getId(), buyer.getPhoneNumber(), List.of("REGISTERED_USER", "BUYER"));

        seller = createTestUser("+99890456");
        sellerToken = jwtTokenProvider.generateAccessToken(seller.getId(), seller.getPhoneNumber(), List.of("REGISTERED_USER", "SELLER"));

        admin = createTestUser("+99890789");
        adminToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getPhoneNumber(), List.of("ADMINISTRATOR", "ADMIN"));

        bystander = createTestUser("+99890999");
        bystanderToken = jwtTokenProvider.generateAccessToken(bystander.getId(), bystander.getPhoneNumber(), List.of("REGISTERED_USER"));
    }

    private UserEntity createTestUser(String prefix) {
        String uniqueSuffix = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        UserEntity user = new UserEntity(prefix + uniqueSuffix, "ACTIVE", "uz");
        user = userRepository.save(user);
        userProfileRepository.save(new UserProfileEntity(user.getId(), "Test Profile " + uniqueSuffix, "Toshkent", "Chilonzor"));
        return user;
    }

    private OrderEntity createCompletedOrder(UserEntity b, UserEntity s) {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                b.getId(),
                s.getId(),
                null,
                null,
                null,
                "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("500000.00"),
                "UZS",
                "COMPLETED",
                "BUYER_ARRANGES",
                "Toshkent, Chilonzor",
                "Standard Delivery",
                "Test order notes",
                null,
                UUID.randomUUID().toString()
        );
        order.setCompletedAt(Instant.now());
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("Scenario 1: Double-blind workflow and atomic unblinding upon dual submission")
    void testDoubleBlindWorkflowAndAtomicPublication() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        // Step 1: Buyer submits review for Seller
        SubmitReviewRequest buyerReq = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                Map.of("accuracy", 5, "communication", 5, "fulfillment", 5),
                "Aʼlo darajadagi xizmat, tovar tez yetkazildi!"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLIND_PENDING"))
                .andExpect(jsonPath("$.counterpartyBlind").value(true))
                .andExpect(jsonPath("$.overallRating").value(5));

        // Step 2: Verify Seller inspects order reviews before submitting
        // Seller sees that buyer submitted, but cannot see commentary or scores
        mockMvc.perform(get("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBuyerSubmitted").value(true))
                .andExpect(jsonPath("$.hasSellerSubmitted").value(false))
                .andExpect(jsonPath("$.isPublished").value(false))
                .andExpect(jsonPath("$.reviews").isEmpty());

        // Step 3: Seller submits review for Buyer
        SubmitReviewRequest sellerReq = new SubmitReviewRequest(
                ReviewType.SELLER_TO_BUYER,
                null,
                4,
                Map.of("communication", 4, "timeliness", 4),
                "Ishonchli xaridor, toʻlov oʻz vaqtida amalga oshirildi."
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.counterpartyBlind").value(false));

        // Step 4: Verify both reviews are now PUBLISHED and visible to both parties
        mockMvc.perform(get("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBuyerSubmitted").value(true))
                .andExpect(jsonPath("$.hasSellerSubmitted").value(true))
                .andExpect(jsonPath("$.isPublished").value(true))
                .andExpect(jsonPath("$.reviews", hasSize(2)));

        // Step 5: Verify Seller public reputation aggregate
        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(seller.getId().toString()))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.totalReviews").value(1));

        // Step 6: Verify Buyer public reputation aggregate
        mockMvc.perform(get("/api/v1/reputation/users/" + buyer.getId() + "?role=BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(buyer.getId().toString()))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    @DisplayName("Scenario 2: Rolling 90-day repeat-pair damping algorithm and equal weighting invariant")
    void test90DayRepeatPairDampingAlgorithm() throws Exception {
        // Buyer 1 performs Deal 1 with Seller (Rating 5)
        OrderEntity deal1 = createCompletedOrder(buyer, seller);
        deal1.setCompletedAt(Instant.now().minus(Duration.ofDays(30)));
        orderRepository.save(deal1);

        ReviewEntity r1 = new ReviewEntity();
        r1.setOrderId(deal1.getId());
        r1.setAuthorId(buyer.getId());
        r1.setTargetUserId(seller.getId());
        r1.setReviewType(ReviewType.BUYER_TO_SELLER);
        r1.setOverallRating(5);
        r1.setStatus(ReviewStatus.PUBLISHED);
        r1.setCounterpartyBlind(false);
        r1.setPublishedAt(Instant.now().minus(Duration.ofDays(30)));
        r1.setCreatedAt(Instant.now().minus(Duration.ofDays(30)));
        reviewRepository.save(r1);

        // Buyer 1 performs Deal 2 with Seller (Rating 3, 10 days later -> within 90 days of Deal 1)
        OrderEntity deal2 = createCompletedOrder(buyer, seller);
        deal2.setCompletedAt(Instant.now().minus(Duration.ofDays(20)));
        orderRepository.save(deal2);

        ReviewEntity r2 = new ReviewEntity();
        r2.setOrderId(deal2.getId());
        r2.setAuthorId(buyer.getId());
        r2.setTargetUserId(seller.getId());
        r2.setReviewType(ReviewType.BUYER_TO_SELLER);
        r2.setOverallRating(3);
        r2.setStatus(ReviewStatus.PUBLISHED);
        r2.setCounterpartyBlind(false);
        r2.setPublishedAt(Instant.now().minus(Duration.ofDays(20)));
        r2.setCreatedAt(Instant.now().minus(Duration.ofDays(20)));
        reviewRepository.save(r2);

        // Deal 1 & Deal 2 from Buyer 1 form Cluster 1 with average score = (5 + 3) / 2 = 4.0

        // Buyer 2 (bystander) performs Deal 3 with Seller (Rating 2)
        OrderEntity deal3 = createCompletedOrder(bystander, seller);
        ReviewEntity r3 = new ReviewEntity();
        r3.setOrderId(deal3.getId());
        r3.setAuthorId(bystander.getId());
        r3.setTargetUserId(seller.getId());
        r3.setReviewType(ReviewType.BUYER_TO_SELLER);
        r3.setOverallRating(2);
        r3.setStatus(ReviewStatus.PUBLISHED);
        r3.setCounterpartyBlind(false);
        r3.setPublishedAt(Instant.now());
        r3.setCreatedAt(Instant.now());
        reviewRepository.save(r3);

        // Cluster 2 from Buyer 2 has score = 2.0
        // Overall Seller Rating = unweighted arithmetic mean of Cluster 1 (4.0) and Cluster 2 (2.0) = 3.00!
        // (If undamped raw average was used, it would have been (5 + 3 + 2) / 3 = 3.33)

        reputationService.recalculateUserReputation(seller.getId(), "SELLER");

        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.totalReviews").value(3));
    }

    @Test
    @DisplayName("Scenario 3: Collusion prevention - unauthorized participant or self-review returns HTTP 403")
    void testPreventSelfReview() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        // Buyer attempts to submit a SELLER_TO_BUYER review (self-review / role mismatch)
        SubmitReviewRequest invalidRoleReq = new SubmitReviewRequest(
                ReviewType.SELLER_TO_BUYER,
                null,
                5,
                null,
                "Oʻzimga oʻzim 5 baho qoʻyaman"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoleReq)))
                .andExpect(status().isForbidden());

        // Uninvolved user attempts to submit review on this order
        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + bystanderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoleReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Scenario 4: Rejection of review on uncompleted order returns HTTP 400")
    void testPreventReviewOnUncompletedOrder() throws Exception {
        OrderEntity pendingOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-PENDING-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(),
                seller.getId(),
                null, null, null,
                "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("100000.00"),
                "UZS",
                "PROCESSING",
                "BUYER_ARRANGES",
                "Toshkent", "Standard", "Notes", null, UUID.randomUUID().toString()
        );
        pendingOrder = orderRepository.save(pendingOrder);

        SubmitReviewRequest req = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                null,
                "Ajoyib"
        );

        mockMvc.perform(post("/api/v1/orders/" + pendingOrder.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Scenario 5: Seller can review CANCELLED with NO_SHOW order")
    void testSellerCanReviewCancelledNoShowOrder() throws Exception {
        OrderEntity noShowOrder = new OrderEntity(
                UUID.randomUUID(),
                "ORD-NO-SHOW-" + UUID.randomUUID().toString().substring(0, 8),
                buyer.getId(),
                seller.getId(),
                null, null, null,
                "INPUT_DIRECT_CHECKOUT",
                new BigDecimal("100000.00"),
                "UZS",
                "CANCELLED",
                "BUYER_ARRANGES",
                "Toshkent", "Standard", "Buyer failed to appear", null, UUID.randomUUID().toString()
        );
        noShowOrder.setCancellationReason("BUYER_NO_SHOW");
        noShowOrder.setCompletedAt(Instant.now());
        noShowOrder = orderRepository.save(noShowOrder);

        SubmitReviewRequest req = new SubmitReviewRequest(
                ReviewType.SELLER_TO_BUYER,
                null,
                1,
                Map.of("commitment", 1),
                "Xaridor yukni qabul qilishga kelmadi va aloqaga chiqmadi."
        );

        mockMvc.perform(post("/api/v1/orders/" + noShowOrder.getId() + "/reviews")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLIND_PENDING"))
                .andExpect(jsonPath("$.overallRating").value(1));
    }

    @Test
    @DisplayName("Scenario 6: Anti-duplication - second review submission returns HTTP 409")
    void testPreventDuplicateReview() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        SubmitReviewRequest req = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                null,
                "Birinchi baho"
        );

        // First submission succeeds
        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second submission returns HTTP 409
        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Scenario 7: 14-day temporal boundary expiration returns HTTP 410")
    void test14DayWindowExpiration() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);
        // Order completed 15 days ago
        order.setCompletedAt(Instant.now().minus(Duration.ofDays(15)));
        orderRepository.save(order);

        SubmitReviewRequest req = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                null,
                "Kechikkan baho"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("REVIEW_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("Scenario 8: Expired blind review publishes automatically after 14 days")
    void testScheduledPublishExpiredBlindReviews() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        SubmitReviewRequest req = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                null,
                "Sotuvchi javob bermasa ham, bu baho 14 kundan keyin chiqishi kerak"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BLIND_PENDING"));

        // Simulate advancing clock by 15 days
        Clock futureClock = Clock.offset(Clock.systemUTC(), Duration.ofDays(15));
        reputationService.setClock(futureClock);

        int publishedCount = reputationService.publishExpiredBlindReviews(Instant.now(futureClock));
        assertTrue(publishedCount >= 1, "At least one expired review should be published");

        // Verify seller's profile now reflects the published review
        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    @DisplayName("Scenario 9: Single factual rebuttal within 7 days, duplicate rejected (409), expired rejected (410)")
    void testFactualRebuttalLifecycle() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        // Buyer creates review, seller creates review -> both PUBLISHED
        ReviewEntity buyerReview = new ReviewEntity();
        buyerReview.setOrderId(order.getId());
        buyerReview.setAuthorId(buyer.getId());
        buyerReview.setTargetUserId(seller.getId());
        buyerReview.setReviewType(ReviewType.BUYER_TO_SELLER);
        buyerReview.setOverallRating(3);
        buyerReview.setCommentary("Tovarni yetkazish 1 kunga kechikdi");
        buyerReview.setStatus(ReviewStatus.PUBLISHED);
        buyerReview.setCounterpartyBlind(false);
        buyerReview.setPublishedAt(Instant.now());
        buyerReview = reviewRepository.save(buyerReview);

        // Step 1: Seller submits factual rebuttal within 7 days
        SubmitRebuttalRequest rebuttalReq = new SubmitRebuttalRequest(
                "Kechikish ob-havo noqulayligi sababli yuz bergan, xaridor ogohlantirilgan."
        );

        mockMvc.perform(post("/api/v1/reputation/reviews/" + buyerReview.getId() + "/rebuttal")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebuttalReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseText").value(rebuttalReq.responseText()));

        // Step 2: Attempt duplicate rebuttal -> HTTP 409
        mockMvc.perform(post("/api/v1/reputation/reviews/" + buyerReview.getId() + "/rebuttal")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebuttalReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Step 3: Test rebuttal on an expired review (published 8 days ago) -> HTTP 410
        ReviewEntity oldReview = new ReviewEntity();
        oldReview.setOrderId(order.getId());
        oldReview.setAuthorId(bystander.getId());
        oldReview.setTargetUserId(seller.getId());
        oldReview.setReviewType(ReviewType.BUYER_TO_SELLER);
        oldReview.setOverallRating(4);
        oldReview.setStatus(ReviewStatus.PUBLISHED);
        oldReview.setCounterpartyBlind(false);
        oldReview.setPublishedAt(Instant.now().minus(Duration.ofDays(8)));
        oldReview.setCreatedAt(Instant.now().minus(Duration.ofDays(8)));
        oldReview = reviewRepository.save(oldReview);

        mockMvc.perform(post("/api/v1/reputation/reviews/" + oldReview.getId() + "/rebuttal")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebuttalReq)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("REVIEW_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("Scenario 10: Abuse report and administrative moderation with immutable audit log")
    void testUserReportAndAdminModerationAudit() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        ReviewEntity review = new ReviewEntity();
        review.setOrderId(order.getId());
        review.setAuthorId(buyer.getId());
        review.setTargetUserId(seller.getId());
        review.setReviewType(ReviewType.BUYER_TO_SELLER);
        review.setOverallRating(1);
        review.setCommentary("Ushbu sotuvchi juda yomon");
        review.setStatus(ReviewStatus.PUBLISHED);
        review.setCounterpartyBlind(false);
        review.setPublishedAt(Instant.now());
        review = reviewRepository.save(review);

        reputationService.recalculateUserReputation(seller.getId(), "SELLER");

        // Verify initial 1-star rating
        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(1.0));

        // Step 1: Seller reports the review for investigation
        ReportReviewRequest reportReq = new ReportReviewRequest(
                "FALSE_FACTS",
                "Buyurtma toʻliq bajarilgan, asossiz tuhmat qilinmoqda"
        );

        mockMvc.perform(post("/api/v1/reputation/reviews/" + review.getId() + "/reports")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").isNotEmpty());

        // Review status transitioned to FLAGGED
        ReviewEntity flaggedReview = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(ReviewStatus.FLAGGED, flaggedReview.getStatus());

        // Step 2: Admin moderates review by HIDING it
        ModerateReviewRequest modReq = new ModerateReviewRequest(
                "HIDE",
                "POLICY_VIOLATION_CONFIRMED",
                "Tekshiruv natijasida xaridor daʼvosi asossiz deb topildi va baho berkitildi"
        );

        mockMvc.perform(post("/api/v1/admin/reputation/reviews/" + review.getId() + "/moderate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MODERATED"))
                .andExpect(jsonPath("$.action").value("HIDE"));

        // Verify review is now HIDDEN
        ReviewEntity hiddenReview = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(ReviewStatus.HIDDEN, hiddenReview.getStatus());

        // Verify immutable audit log record was created (ADM-005)
        List<ReviewModerationLogEntity> auditLogs = reviewModerationLogRepository.findByReviewIdOrderByRecordedAtDesc(review.getId());
        assertFalse(auditLogs.isEmpty(), "Audit log must be recorded");
        assertEquals("REVIEW_HIDE", auditLogs.get(0).getAction());
        assertEquals("POLICY_VIOLATION_CONFIRMED", auditLogs.get(0).getJustificationCode());
        assertEquals(admin.getId(), auditLogs.get(0).getAdminUserId());

        // Verify hidden review is excluded from Seller's public aggregate rating
        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    @DisplayName("Scenario 11: Content policy detection - PII and abusive slurs return HTTP 422")
    void testContentPolicyViolation() throws Exception {
        OrderEntity order = createCompletedOrder(buyer, seller);

        // Attempt 1: Commentary contains a phone number
        SubmitReviewRequest phoneReq = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                5,
                null,
                "Menga qoʻngʻiroq qiling: +998 90 123 45 67"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(phoneReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTENT_POLICY_VIOLATION"));

        // Attempt 2: Commentary contains abusive language
        SubmitReviewRequest abusiveReq = new SubmitReviewRequest(
                ReviewType.BUYER_TO_SELLER,
                null,
                1,
                null,
                "Bu odam muttaham va firibgar!"
        );

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abusiveReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTENT_POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("Scenario 12: Public reputation endpoints allow unauthenticated access")
    void testPublicReputationEndpointsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "?role=SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(seller.getId().toString()));

        mockMvc.perform(get("/api/v1/reputation/users/" + seller.getId() + "/reviews"))
                .andExpect(status().isOk());
    }
}
