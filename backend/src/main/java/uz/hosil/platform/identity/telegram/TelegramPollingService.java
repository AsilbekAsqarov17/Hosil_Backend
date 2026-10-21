package uz.hosil.platform.identity.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-polling worker for receiving Telegram bot updates during local development per Milestone 6C.5.
 * Strictly disabled by default. Activated only when hosil.telegram.polling.enabled=true in local/dev profiles.
 * In production environments, a secured HTTPS webhook must be used instead of long polling.
 */
@Service
@ConditionalOnProperty(name = "hosil.telegram.polling.enabled", havingValue = "true")
public class TelegramPollingService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramApiClient telegramApiClient;
    private final TelegramLinkService telegramLinkService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollingThread;
    private long offset = 0;

    public TelegramPollingService(
            TelegramApiClient telegramApiClient,
            TelegramLinkService telegramLinkService
    ) {
        this.telegramApiClient = telegramApiClient;
        this.telegramLinkService = telegramLinkService;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting Telegram Bot long-polling daemon for local development...");
            log.warn("Notice: Long polling is intended exclusively for local development without a public HTTPS endpoint. Production should use an authentic webhook.");

            pollingThread = new Thread(this::pollLoop, "hosil-telegram-poller");
            pollingThread.setDaemon(true);
            pollingThread.start();
        }
    }

    private void pollLoop() {
        int backoffMs = 2000;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<TelegramModels.TelegramUpdate> updates = telegramApiClient.getUpdates(
                        offset > 0 ? offset : null,
                        100,
                        20
                );

                for (TelegramModels.TelegramUpdate update : updates) {
                    if (!running.get()) break;
                    if (update.updateId() != null) {
                        try {
                            telegramLinkService.processUpdate(update);
                        } catch (Exception e) {
                            log.error("Error processing Telegram update {}: {}", update.updateId(), e.getMessage(), e);
                        }
                        if (update.updateId() >= offset) {
                            offset = update.updateId() + 1;
                        }
                    }
                }

                // Reset backoff on successful cycle
                backoffMs = 2000;
            } catch (Exception e) {
                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.warn("Telegram polling error: {}. Backing off for {} ms", e.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30000); // Max 30 seconds backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("Telegram polling daemon terminated.");
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping Telegram Bot long-polling daemon...");
            if (pollingThread != null) {
                pollingThread.interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // Start last, stop first
    }
}
