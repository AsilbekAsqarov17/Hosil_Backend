package uz.hosil.platform.reputation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ReviewWindowExpiredException;
import uz.hosil.platform.common.error.ValidationException;
import uz.hosil.platform.marketplace.OrderItemReviewSummary;
import uz.hosil.platform.marketplace.OrderReviewEligibilitySummary;
import uz.hosil.platform.marketplace.MarketplaceOrderBridge;
import uz.hosil.platform.reputation.domain.ReviewEntity;
import uz.hosil.platform.reputation.domain.ReviewModerationLogEntity;
import uz.hosil.platform.reputation.domain.ReviewReportEntity;
import uz.hosil.platform.reputation.domain.ReviewReportStatus;
import uz.hosil.platform.reputation.domain.ReviewResponseEntity;
import uz.hosil.platform.reputation.domain.ReviewStatus;
import uz.hosil.platform.reputation.domain.ReviewType;
import uz.hosil.platform.reputation.domain.UserReputationAggregateEntity;
import uz.hosil.platform.reputation.repository.ReviewModerationLogRepository;
import uz.hosil.platform.reputation.repository.ReviewReportRepository;
import uz.hosil.platform.reputation.repository.ReviewRepository;
import uz.hosil.platform.reputation.repository.ReviewResponseRepository;
import uz.hosil.platform.reputation.repository.UserReputationAggregateRepository;
import uz.hosil.platform.reputation.web.ModerateReviewRequest;
import uz.hosil.platform.reputation.web.OrderReviewsSummaryDto;
import uz.hosil.platform.reputation.web.ProductReputationSummaryDto;
import uz.hosil.platform.reputation.web.ReportReviewRequest;
import uz.hosil.platform.reputation.web.ReviewDto;
import uz.hosil.platform.reputation.web.ReviewResponseDto;
import uz.hosil.platform.reputation.web.SubmitRebuttalRequest;
import uz.hosil.platform.reputation.web.SubmitReviewRequest;
import uz.hosil.platform.reputation.web.UserReputationSummaryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing two-sided reviews, double-blind windows, rebuttals, 90-day repeat-pair damping,
 * and audited administrative moderation.
 * Source of Truth: docs/12_reputation_and_reviews.md, DEC-018.
 */
@Service
public class ReputationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewResponseRepository reviewResponseRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewModerationLogRepository reviewModerationLogRepository;
    private final UserReputationAggregateRepository aggregateRepository;
    private final MarketplaceOrderBridge marketplaceOrderBridge;
    private final ContentPolicyValidator contentPolicyValidator;
    private final ObjectMapper objectMapper;
    private Clock clock;

    public ReputationService(
            ReviewRepository reviewRepository,
            ReviewResponseRepository reviewResponseRepository,
            ReviewReportRepository reviewReportRepository,
            ReviewModerationLogRepository reviewModerationLogRepository,
            UserReputationAggregateRepository aggregateRepository,
            MarketplaceOrderBridge marketplaceOrderBridge,
            ContentPolicyValidator contentPolicyValidator,
            ObjectMapper objectMapper
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewResponseRepository = reviewResponseRepository;
        this.reviewReportRepository = reviewReportRepository;
        this.reviewModerationLogRepository = reviewModerationLogRepository;
        this.aggregateRepository = aggregateRepository;
        this.marketplaceOrderBridge = marketplaceOrderBridge;
        this.contentPolicyValidator = contentPolicyValidator;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return this.clock;
    }

    /**
     * Submits a transaction-gated review (AC-REV-1..AC-REV-5).
     */
    @Transactional
    public ReviewDto submitReview(UUID orderId, UUID currentUserId, SubmitReviewRequest request) {
        contentPolicyValidator.validateText(request.commentary(), "commentary");

        OrderReviewEligibilitySummary order = marketplaceOrderBridge.findOrderForReview(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        // Self-review prevention (ABAC rule)
        if (order.buyerUserId().equals(order.sellerUserId())) {
            throw new AccessDeniedException("CANNOT_REVIEW_SELF: Oʻz-oʻzini baholash taqiqlangan");
        }

        UUID targetUserId = null;
        UUID targetListingId = null;
        UUID orderItemId = null;

        if (request.reviewType() == ReviewType.BUYER_TO_SELLER) {
            if (!order.buyerUserId().equals(currentUserId)) {
                throw new AccessDeniedException("Faqat buyurtmaning haqiqiy xaridori sotuvchini baholashi mumkin");
            }
            if (!"COMPLETED".equalsIgnoreCase(order.status())) {
                throw new ValidationException("ORDER_NOT_COMPLETED: Baholash faqat COMPLETED holatidagi buyurtmalar uchun mumkin");
            }
            targetUserId = order.sellerUserId();
        } else if (request.reviewType() == ReviewType.SELLER_TO_BUYER) {
            if (!order.sellerUserId().equals(currentUserId)) {
                throw new AccessDeniedException("Faqat buyurtmaning haqiqiy sotuvchisi xaridorni baholashi mumkin");
            }
            boolean eligibleStatus = "COMPLETED".equalsIgnoreCase(order.status())
                    || "CANCELLED_NO_SHOW".equalsIgnoreCase(order.status())
                    || ("CANCELLED".equalsIgnoreCase(order.status()) && order.cancellationReason() != null && order.cancellationReason().toUpperCase().contains("NO_SHOW"));
            if (!eligibleStatus) {
                throw new ValidationException("ORDER_NOT_COMPLETED: Xaridorni baholash faqat COMPLETED yoki NO_SHOW holatidagi buyurtmalar uchun mumkin");
            }
            targetUserId = order.buyerUserId();
        } else if (request.reviewType() == ReviewType.LISTING_PRODUCT) {
            if (!order.buyerUserId().equals(currentUserId)) {
                throw new AccessDeniedException("Faqat buyurtmaning haqiqiy xaridori mahsulotni baholashi mumkin");
            }
            if (!"COMPLETED".equalsIgnoreCase(order.status())) {
                throw new ValidationException("ORDER_NOT_COMPLETED: Mahsulotni baholash faqat COMPLETED buyurtmalar uchun mumkin");
            }
            targetUserId = order.sellerUserId();

            // Locate order item
            OrderItemReviewSummary item = null;
            if (request.orderItemId() != null) {
                item = order.items().stream()
                        .filter(i -> i.orderItemId().equals(request.orderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new ValidationException("Koʻrsatilgan mahsulot buyurtmada mavjud emas: " + request.orderItemId()));
            } else if (!order.items().isEmpty()) {
                item = order.items().get(0);
            } else {
                throw new ValidationException("Mahsulotni baholash uchun buyurtma tarkibida tovarlar mavjud boʻlishi shart");
            }

            orderItemId = item.orderItemId();
            targetListingId = item.harvestListingId() != null ? item.harvestListingId()
                    : (item.sellerOfferId() != null ? item.sellerOfferId() : item.catalogProductId());
        }

        // 14-day temporal boundary check
        Instant referenceTime = order.completedAt() != null ? order.completedAt() : Instant.now(clock);
        Instant blindUntil = referenceTime.plus(14, ChronoUnit.DAYS);
        if (Instant.now(clock).isAfter(blindUntil)) {
            throw new ReviewWindowExpiredException("REVIEW_WINDOW_EXPIRED: 14 kunlik baholash muddati tugagan");
        }

        // Duplicate review prevention (AC-REV-2)
        if (request.reviewType() == ReviewType.LISTING_PRODUCT) {
            if (reviewRepository.findByOrderIdAndOrderItemIdAndAuthorId(orderId, orderItemId, currentUserId).isPresent()) {
                throw new BusinessConflictException("REVIEW_ALREADY_EXISTS: Ushbu tovar boʻyicha baho allaqachon qoldirilgan");
            }
        } else {
            if (reviewRepository.findByOrderIdAndAuthorIdAndReviewType(orderId, currentUserId, request.reviewType()).isPresent()) {
                throw new BusinessConflictException("REVIEW_ALREADY_EXISTS: Ushbu buyurtma boʻyicha baho allaqachon qoldirilgan");
            }
        }

        ReviewEntity review = new ReviewEntity();
        review.setOrderId(orderId);
        review.setOrderItemId(orderItemId);
        review.setAuthorId(currentUserId);
        review.setTargetUserId(targetUserId);
        review.setTargetListingId(targetListingId);
        review.setReviewType(request.reviewType());
        review.setOverallRating(request.overallRating());
        review.setDimensionScores(serializeScores(request.dimensionScores()));
        review.setCommentary(request.commentary());
        review.setStatus(ReviewStatus.BLIND_PENDING);
        review.setCounterpartyBlind(true);
        review.setBlindUntil(blindUntil);
        review.setCreatedAt(Instant.now(clock));
        review.setUpdatedAt(Instant.now(clock));

        review = reviewRepository.saveAndFlush(review);

        // Check if atomic unblinding conditions are satisfied
        triggerAtomicPublishIfEligible(orderId);

        // Re-read review to obtain latest published status
        review = reviewRepository.findById(review.getId()).orElse(review);

        return toDto(review, null);
    }

    /**
     * Unblinds and publishes both counterparty reviews if both buyer and seller have submitted (AC-REV-5).
     */
    private void triggerAtomicPublishIfEligible(UUID orderId) {
        List<ReviewEntity> orderReviews = reviewRepository.findByOrderId(orderId);

        boolean hasBuyerReview = orderReviews.stream()
                .anyMatch(r -> r.getReviewType() == ReviewType.BUYER_TO_SELLER);
        boolean hasSellerReview = orderReviews.stream()
                .anyMatch(r -> r.getReviewType() == ReviewType.SELLER_TO_BUYER);

        if (hasBuyerReview && hasSellerReview) {
            Instant now = Instant.now(clock);
            for (ReviewEntity r : orderReviews) {
                if (r.getStatus() == ReviewStatus.BLIND_PENDING) {
                    r.setStatus(ReviewStatus.PUBLISHED);
                    r.setCounterpartyBlind(false);
                    r.setPublishedAt(now);
                    reviewRepository.save(r);
                }
            }

            // Recalculate aggregates for both participants
            Set<UUID> targetUserIds = orderReviews.stream()
                    .map(ReviewEntity::getTargetUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (UUID targetId : targetUserIds) {
                recalculateUserReputation(targetId, "SELLER");
                recalculateUserReputation(targetId, "BUYER");
            }
        }
    }

    /**
     * Scheduled or manual sweep for expired blind reviews (14 calendar days elapsed).
     */
    @Transactional
    public int publishExpiredBlindReviews(Instant asOf) {
        Instant cutoff = asOf != null ? asOf : Instant.now(clock);
        List<ReviewEntity> expired = reviewRepository.findByStatusAndBlindUntilLessThanEqual(
                ReviewStatus.BLIND_PENDING, cutoff
        );

        if (expired.isEmpty()) {
            return 0;
        }

        for (ReviewEntity r : expired) {
            r.setStatus(ReviewStatus.PUBLISHED);
            r.setCounterpartyBlind(false);
            r.setPublishedAt(cutoff);
            reviewRepository.save(r);
        }

        Set<UUID> targetUserIds = expired.stream()
                .map(ReviewEntity::getTargetUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (UUID targetId : targetUserIds) {
            recalculateUserReputation(targetId, "SELLER");
            recalculateUserReputation(targetId, "BUYER");
        }

        return expired.size();
    }

    /**
     * Submits a single factual counterparty rebuttal within 7 calendar days of publication (AC-REV-6).
     */
    @Transactional
    public ReviewResponseDto submitRebuttal(UUID reviewId, UUID currentUserId, SubmitRebuttalRequest request) {
        contentPolicyValidator.validateText(request.responseText(), "responseText");

        ReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Baholash topilmadi: " + reviewId));

        if (review.getStatus() != ReviewStatus.PUBLISHED) {
            throw new ValidationException("Raddiya faqat chop etilgan (PUBLISHED) baholarga bildirilishi mumkin");
        }

        if (review.getTargetUserId() == null || !review.getTargetUserId().equals(currentUserId)) {
            throw new AccessDeniedException("Faqat baholangan tomon raddiya bildira oladi");
        }

        // 7-day boundary check
        Instant pubAt = review.getPublishedAt() != null ? review.getPublishedAt() : review.getCreatedAt();
        if (Instant.now(clock).isAfter(pubAt.plus(7, ChronoUnit.DAYS))) {
            throw new ReviewWindowExpiredException("REBUTTAL_WINDOW_EXPIRED: 7 kunlik raddiya bildirish muddati tugagan");
        }

        if (reviewResponseRepository.findByReviewId(reviewId).isPresent()) {
            throw new BusinessConflictException("REBUTTAL_ALREADY_EXISTS: Ushbu bahoga allaqachon raddiya berilgan");
        }

        ReviewResponseEntity response = new ReviewResponseEntity();
        response.setReviewId(reviewId);
        response.setAuthorId(currentUserId);
        response.setResponseText(request.responseText());
        response.setCreatedAt(Instant.now(clock));

        response = reviewResponseRepository.save(response);

        return new ReviewResponseDto(
                response.getId(),
                response.getReviewId(),
                response.getAuthorId(),
                response.getResponseText(),
                response.getCreatedAt()
        );
    }

    /**
     * Reports a published review for moderation inspection.
     */
    @Transactional
    public UUID reportReview(UUID reviewId, UUID reporterUserId, ReportReviewRequest request) {
        ReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Baholash topilmadi: " + reviewId));

        ReviewReportEntity report = new ReviewReportEntity();
        report.setReviewId(reviewId);
        report.setReporterId(reporterUserId);
        report.setReasonCode(request.reasonCode());
        report.setDetails(request.details());
        report.setStatus(ReviewReportStatus.PENDING);
        report.setCreatedAt(Instant.now(clock));

        report = reviewReportRepository.save(report);

        if (review.getStatus() == ReviewStatus.PUBLISHED) {
            review.setStatus(ReviewStatus.FLAGGED);
            reviewRepository.save(review);
        }

        return report.getId();
    }

    /**
     * Administrative moderation action with mandatory immutable audit log (AC-REV-8, ADM-005).
     */
    @Transactional
    public void moderateReview(UUID reviewId, UUID adminUserId, ModerateReviewRequest request) {
        ReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Baholash topilmadi: " + reviewId));

        String action = request.action().toUpperCase();
        switch (action) {
            case "HIDE" -> review.setStatus(ReviewStatus.HIDDEN);
            case "RESTORE" -> review.setStatus(ReviewStatus.PUBLISHED);
            case "REMOVE" -> review.setStatus(ReviewStatus.REMOVED);
            case "DISMISS_REPORT" -> {
                if (review.getStatus() == ReviewStatus.FLAGGED) {
                    review.setStatus(ReviewStatus.PUBLISHED);
                }
                List<ReviewReportEntity> reports = reviewReportRepository.findByReviewId(reviewId);
                for (ReviewReportEntity r : reports) {
                    if (r.getStatus() == ReviewReportStatus.PENDING) {
                        r.setStatus(ReviewReportStatus.DISMISSED);
                        r.setResolvedAt(Instant.now(clock));
                        r.setResolutionNotes(request.adminNotes());
                        reviewReportRepository.save(r);
                    }
                }
            }
            default -> throw new ValidationException("Nomaʼlum moderatsiya amali: " + action);
        }

        if ("HIDE".equals(action) || "REMOVE".equals(action)) {
            List<ReviewReportEntity> reports = reviewReportRepository.findByReviewId(reviewId);
            for (ReviewReportEntity r : reports) {
                if (r.getStatus() == ReviewReportStatus.PENDING) {
                    r.setStatus(ReviewReportStatus.ACTIONED);
                    r.setResolvedAt(Instant.now(clock));
                    r.setResolutionNotes(request.adminNotes());
                    reviewReportRepository.save(r);
                }
            }
        }

        reviewRepository.save(review);

        // Immutable audit log
        ReviewModerationLogEntity logEntry = new ReviewModerationLogEntity(
                reviewId,
                adminUserId,
                "REVIEW_" + action,
                request.justificationCode(),
                request.adminNotes()
        );
        logEntry.setRecordedAt(Instant.now(clock));
        reviewModerationLogRepository.save(logEntry);

        // Recalculate target aggregates
        if (review.getTargetUserId() != null) {
            recalculateUserReputation(review.getTargetUserId(), "SELLER");
            recalculateUserReputation(review.getTargetUserId(), "BUYER");
        }
    }

    /**
     * Returns order review status and reviews respecting double-blind privacy (AC-REV-5).
     */
    @Transactional(readOnly = true)
    public OrderReviewsSummaryDto getOrderReviewsSummary(UUID orderId, UUID currentUserId) {
        OrderReviewEligibilitySummary order = marketplaceOrderBridge.findOrderForReview(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        if (!order.buyerUserId().equals(currentUserId) && !order.sellerUserId().equals(currentUserId)) {
            throw new AccessDeniedException("Siz ushbu buyurtma ishtirokchisi emassiz");
        }

        List<ReviewEntity> allOrderReviews = reviewRepository.findByOrderId(orderId);

        boolean hasBuyerSubmitted = allOrderReviews.stream()
                .anyMatch(r -> r.getReviewType() == ReviewType.BUYER_TO_SELLER);
        boolean hasSellerSubmitted = allOrderReviews.stream()
                .anyMatch(r -> r.getReviewType() == ReviewType.SELLER_TO_BUYER);
        boolean isPublished = allOrderReviews.stream()
                .anyMatch(r -> r.getStatus() == ReviewStatus.PUBLISHED);

        List<UUID> reviewIds = allOrderReviews.stream().map(ReviewEntity::getId).toList();
        Map<UUID, ReviewResponseEntity> responses = reviewResponseRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(ReviewResponseEntity::getReviewId, resp -> resp));

        List<ReviewDto> visibleReviews = new ArrayList<>();
        for (ReviewEntity r : allOrderReviews) {
            // Author can always view own review; counterparty can only view if published
            if (r.getAuthorId().equals(currentUserId) || r.getStatus() == ReviewStatus.PUBLISHED) {
                visibleReviews.add(toDto(r, responses.get(r.getId())));
            }
        }

        return new OrderReviewsSummaryDto(
                orderId,
                hasBuyerSubmitted,
                hasSellerSubmitted,
                isPublished,
                visibleReviews
        );
    }

    /**
     * Recalculates user reputation using transparent arithmetic average and 90-day repeat-pair damping (§4.1 & §4.2).
     */
    @Transactional
    public UserReputationSummaryDto recalculateUserReputation(UUID targetUserId, String roleContext) {
        ReviewType targetReviewType = "SELLER".equalsIgnoreCase(roleContext)
                ? ReviewType.BUYER_TO_SELLER
                : ReviewType.SELLER_TO_BUYER;

        List<ReviewEntity> publishedReviews = reviewRepository.findByTargetUserIdAndReviewTypeAndStatus(
                targetUserId, targetReviewType, ReviewStatus.PUBLISHED
        );

        long completedOrders = marketplaceOrderBridge.countCompletedOrdersForUser(targetUserId);

        UserReputationAggregateEntity aggregate = aggregateRepository.findByUserIdAndRoleContext(targetUserId, roleContext.toUpperCase())
                .orElseGet(() -> new UserReputationAggregateEntity(targetUserId, roleContext.toUpperCase()));

        if (publishedReviews.isEmpty()) {
            aggregate.setAverageRating(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            aggregate.setTotalReviews(0);
            aggregate.setTotalCompletedOrders((int) completedOrders);
            aggregate.setDimensionAverages(null);
            aggregateRepository.save(aggregate);

            return new UserReputationSummaryDto(
                    targetUserId,
                    roleContext.toUpperCase(),
                    0.0,
                    0,
                    (int) completedOrders,
                    Collections.emptyMap()
            );
        }

        // Apply 90-day repeat-pair damping algorithm:
        // Group published reviews by author
        Map<UUID, List<ReviewEntity>> byAuthor = publishedReviews.stream()
                .collect(Collectors.groupingBy(ReviewEntity::getAuthorId));

        List<Double> clusterScores = new ArrayList<>();

        for (List<ReviewEntity> authorReviews : byAuthor.values()) {
            authorReviews.sort(Comparator.comparing(r -> r.getCreatedAt() != null ? r.getCreatedAt() : Instant.EPOCH));

            Instant currentClusterStart = null;
            List<Integer> currentClusterRatings = new ArrayList<>();

            for (ReviewEntity r : authorReviews) {
                Instant rTime = r.getCreatedAt() != null ? r.getCreatedAt() : Instant.now(clock);
                if (currentClusterStart == null || rTime.isAfter(currentClusterStart.plus(90, ChronoUnit.DAYS))) {
                    if (!currentClusterRatings.isEmpty()) {
                        double clusterAvg = currentClusterRatings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                        clusterScores.add(clusterAvg);
                        currentClusterRatings.clear();
                    }
                    currentClusterStart = rTime;
                }
                currentClusterRatings.add(r.getOverallRating());
            }

            if (!currentClusterRatings.isEmpty()) {
                double clusterAvg = currentClusterRatings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                clusterScores.add(clusterAvg);
            }
        }

        // Arithmetic mean across clusters (Equal Weighting AC-REV-3)
        double finalAverage = clusterScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        BigDecimal roundedAverage = BigDecimal.valueOf(finalAverage).setScale(2, RoundingMode.HALF_UP);

        // Calculate dimension averages across all published reviews
        Map<String, Double> dimensionAverages = calculateDimensionAverages(publishedReviews);

        aggregate.setAverageRating(roundedAverage);
        aggregate.setTotalReviews(publishedReviews.size());
        aggregate.setTotalCompletedOrders((int) completedOrders);
        aggregate.setDimensionAverages(serializeDimensionAverages(dimensionAverages));
        aggregateRepository.save(aggregate);

        return new UserReputationSummaryDto(
                targetUserId,
                roleContext.toUpperCase(),
                roundedAverage.doubleValue(),
                publishedReviews.size(),
                (int) completedOrders,
                dimensionAverages
        );
    }

    /**
     * Retrieves user reputation summary.
     */
    @Transactional
    public UserReputationSummaryDto getUserReputation(UUID userId, String roleContext) {
        String role = roleContext != null ? roleContext.toUpperCase() : "SELLER";
        return aggregateRepository.findByUserIdAndRoleContext(userId, role)
                .map(agg -> new UserReputationSummaryDto(
                        agg.getUserId(),
                        agg.getRoleContext(),
                        agg.getAverageRating().doubleValue(),
                        agg.getTotalReviews(),
                        agg.getTotalCompletedOrders(),
                        deserializeDimensionAverages(agg.getDimensionAverages())
                ))
                .orElseGet(() -> recalculateUserReputation(userId, role));
    }

    /**
     * Retrieves published reviews for a user.
     */
    @Transactional(readOnly = true)
    public List<ReviewDto> getUserReviews(UUID userId, ReviewType reviewType) {
        List<ReviewEntity> reviews = reviewRepository.findByTargetUserIdAndStatusOrderByPublishedAtDesc(
                userId, ReviewStatus.PUBLISHED
        );

        if (reviewType != null) {
            reviews = reviews.stream().filter(r -> r.getReviewType() == reviewType).toList();
        }

        List<UUID> reviewIds = reviews.stream().map(ReviewEntity::getId).toList();
        Map<UUID, ReviewResponseEntity> responses = reviewResponseRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(ReviewResponseEntity::getReviewId, resp -> resp));

        return reviews.stream()
                .map(r -> toDto(r, responses.get(r.getId())))
                .toList();
    }

    /**
     * Retrieves product reputation summary for a listing.
     */
    @Transactional(readOnly = true)
    public ProductReputationSummaryDto getListingReputation(UUID listingId) {
        List<ReviewEntity> published = reviewRepository.findByTargetListingIdAndStatusOrderByPublishedAtDesc(
                listingId, ReviewStatus.PUBLISHED
        );

        if (published.isEmpty()) {
            return new ProductReputationSummaryDto(listingId, 0.0, 0, Collections.emptyMap());
        }

        double avg = published.stream().mapToInt(ReviewEntity::getOverallRating).average().orElse(0.0);
        BigDecimal rounded = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        Map<String, Double> dims = calculateDimensionAverages(published);

        return new ProductReputationSummaryDto(
                listingId,
                rounded.doubleValue(),
                published.size(),
                dims
        );
    }

    /**
     * Retrieves published product reviews for a listing.
     */
    @Transactional(readOnly = true)
    public List<ReviewDto> getListingReviews(UUID listingId) {
        List<ReviewEntity> reviews = reviewRepository.findByTargetListingIdAndStatusOrderByPublishedAtDesc(
                listingId, ReviewStatus.PUBLISHED
        );

        List<UUID> reviewIds = reviews.stream().map(ReviewEntity::getId).toList();
        Map<UUID, ReviewResponseEntity> responses = reviewResponseRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(ReviewResponseEntity::getReviewId, resp -> resp));

        return reviews.stream()
                .map(r -> toDto(r, responses.get(r.getId())))
                .toList();
    }

    // --- Helper mapping methods ---

    private Map<String, Double> calculateDimensionAverages(List<ReviewEntity> reviews) {
        Map<String, List<Integer>> dimMap = new HashMap<>();
        for (ReviewEntity r : reviews) {
            Map<String, Integer> scores = deserializeScores(r.getDimensionScores());
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                dimMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }

        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : dimMap.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            result.put(entry.getKey(), BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        return result;
    }

    private ReviewDto toDto(ReviewEntity r, ReviewResponseEntity rebuttal) {
        ReviewResponseDto rebuttalDto = null;
        if (rebuttal != null) {
            rebuttalDto = new ReviewResponseDto(
                    rebuttal.getId(),
                    rebuttal.getReviewId(),
                    rebuttal.getAuthorId(),
                    rebuttal.getResponseText(),
                    rebuttal.getCreatedAt()
            );
        }
        return new ReviewDto(
                r.getId(),
                r.getOrderId(),
                r.getOrderItemId(),
                r.getAuthorId(),
                r.getTargetUserId(),
                r.getTargetListingId(),
                r.getReviewType(),
                r.getOverallRating(),
                deserializeScores(r.getDimensionScores()),
                r.getCommentary(),
                r.getStatus(),
                r.isCounterpartyBlind(),
                r.getPublishedAt(),
                r.getCreatedAt(),
                rebuttalDto
        );
    }

    private String serializeScores(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize dimension scores: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Integer> deserializeScores(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize dimension scores: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String serializeDimensionAverages(Map<String, Double> averages) {
        if (averages == null || averages.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(averages);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Double> deserializeDimensionAverages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
