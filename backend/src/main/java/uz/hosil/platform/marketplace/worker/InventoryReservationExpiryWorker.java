package uz.hosil.platform.marketplace.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.hosil.platform.marketplace.config.InventoryReservationExpiryProperties;
import uz.hosil.platform.marketplace.service.InventoryReservationExpiryService;

import java.util.Optional;

/**
 * Scheduled background worker that polls for expired active inventory reservations
 * and safely releases reserved stock back to available listings (DEC-006, DEC-010).
 */
@Component
@ConditionalOnProperty(name = "hosil.inventory.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReservationExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationExpiryWorker.class);

    private final InventoryReservationExpiryService expiryService;
    private final InventoryReservationExpiryProperties properties;
    private final Optional<Counter> releasedReservationsCounter;
    private final Optional<Counter> sweepRunsCounter;
    private final Optional<Counter> sweepFailuresCounter;

    public InventoryReservationExpiryWorker(
            InventoryReservationExpiryService expiryService,
            InventoryReservationExpiryProperties properties,
            Optional<MeterRegistry> meterRegistry
    ) {
        this.expiryService = expiryService;
        this.properties = properties;
        this.releasedReservationsCounter = meterRegistry.map(m -> m.counter("inventory.reservations.expired.released"));
        this.sweepRunsCounter = meterRegistry.map(m -> m.counter("inventory.reservations.expiry.sweep.runs"));
        this.sweepFailuresCounter = meterRegistry.map(m -> m.counter("inventory.reservations.expiry.sweep.failures"));
    }

    @Scheduled(fixedDelayString = "${hosil.inventory.expiry.polling-interval-ms:60000}")
    public void runExpirySweep() {
        log.debug("Starting scheduled inventory reservation expiry sweep: batchSize={}", properties.batchSize());
        sweepRunsCounter.ifPresent(Counter::increment);
        try {
            int released = expiryService.sweepExpiredReservations(properties.batchSize());
            if (released > 0) {
                releasedReservationsCounter.ifPresent(c -> c.increment(released));
                log.info("Scheduled inventory reservation expiry sweep completed: releasedCount={}", released);
            }
        } catch (Exception ex) {
            sweepFailuresCounter.ifPresent(Counter::increment);
            log.error("Unhandled error during scheduled inventory reservation expiry sweep", ex);
        }
    }
}
