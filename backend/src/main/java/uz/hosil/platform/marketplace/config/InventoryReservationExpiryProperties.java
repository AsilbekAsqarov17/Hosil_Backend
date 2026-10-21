package uz.hosil.platform.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hosil.inventory.expiry")
public record InventoryReservationExpiryProperties(
        boolean enabled,
        long pollingIntervalMs,
        int batchSize
) {
    public InventoryReservationExpiryProperties {
        if (pollingIntervalMs <= 0) {
            pollingIntervalMs = 60000;
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
    }
}
