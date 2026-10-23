package uz.hosil.platform.reputation.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.hosil.platform.reputation.service.ReputationService;

import java.time.Instant;

/**
 * Scheduled background worker that automatically identifies and publishes reviews
 * whose 14-day double-blind publication window has expired (REV-004, DEC-018).
 */
@Component
@ConditionalOnProperty(name = "hosil.reputation.blind-expiration.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewExpirationWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewExpirationWorker.class);

    private final ReputationService reputationService;

    public ReviewExpirationWorker(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Scheduled(fixedDelayString = "${hosil.reputation.blind-expiration.polling-interval-ms:3600000}")
    public void runBlindReviewExpirySweep() {
        log.debug("Starting scheduled blind review expiry sweep");
        try {
            int published = reputationService.publishExpiredBlindReviews(Instant.now());
            if (published > 0) {
                log.info("Scheduled blind review expiry sweep completed: publishedCount={}", published);
            }
        } catch (Exception ex) {
            log.error("Unhandled error during scheduled blind review expiry sweep", ex);
        }
    }
}
