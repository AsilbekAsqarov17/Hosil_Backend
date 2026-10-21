package uz.hosil.platform.identity.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production HTTP client adapter for the Telegram Bot API using standard java.net.http.HttpClient.
 * Enforces strict security: never logs bot tokens or message bodies.
 */
@Component
public class HttpTelegramApiClient implements TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTelegramApiClient.class);

    private final String botToken;
    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpTelegramApiClient(
            @Value("${hosil.telegram.bot-token:}") String rawBotToken,
            @Value("${hosil.telegram.api-url:https://api.telegram.org}") String apiUrl,
            ObjectMapper objectMapper
    ) {
        this.botToken = rawBotToken != null ? rawBotToken.trim() : "";
        this.apiUrl = apiUrl != null ? apiUrl.trim().replaceAll("/+$", "") : "https://api.telegram.org";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    private String getEndpoint(String method) {
        if (botToken.isBlank()) {
            throw new IllegalStateException("HOSIL_BOT_TOKEN is not configured for Telegram API integration");
        }
        return apiUrl + "/bot" + botToken + "/" + method;
    }

    @Override
    public TelegramModels.TelegramUser getMe() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getEndpoint("getMe")))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            TelegramModels.TelegramGetMeResponse res = objectMapper.readValue(
                    response.body(),
                    TelegramModels.TelegramGetMeResponse.class
            );

            if (res.ok() != null && res.ok()) {
                return res.result();
            }
            throw new TelegramApiException("Telegram getMe failed: " + res.description());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TelegramApiException("Telegram getMe error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TelegramModels.TelegramUpdate> getUpdates(Long offset, Integer limit, Integer timeoutSeconds) {
        try {
            Map<String, Object> payload = new HashMap<>();
            if (offset != null) {
                payload.put("offset", offset);
            }
            if (limit != null) {
                payload.put("limit", limit);
            }
            if (timeoutSeconds != null) {
                payload.put("timeout", timeoutSeconds);
            }

            String json = objectMapper.writeValueAsString(payload);
            int requestTimeout = (timeoutSeconds != null ? timeoutSeconds : 0) + 10;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getEndpoint("getUpdates")))
                    .timeout(Duration.ofSeconds(requestTimeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            TelegramModels.TelegramGetUpdatesResponse res = objectMapper.readValue(
                    response.body(),
                    TelegramModels.TelegramGetUpdatesResponse.class
            );

            if (res.ok() != null && res.ok()) {
                return res.result() != null ? res.result() : List.of();
            }
            throw new TelegramApiException("Telegram getUpdates rejected: " + res.description());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TelegramApiException("Telegram getUpdates network error: " + e.getMessage(), e);
        }
    }

    @Override
    public TelegramModels.TelegramMessage sendMessage(Long chatId, String text, String parseMode, Object replyMarkup) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            if (parseMode != null && !parseMode.isBlank()) {
                payload.put("parse_mode", parseMode);
            }
            if (replyMarkup != null) {
                payload.put("reply_markup", replyMarkup);
            }

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getEndpoint("sendMessage")))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            TelegramModels.TelegramSendMessageResponse res = objectMapper.readValue(
                    response.body(),
                    TelegramModels.TelegramSendMessageResponse.class
            );

            if (res.ok() != null && res.ok()) {
                log.info("Telegram message dispatched successfully to chat ID: {}", maskChatId(chatId));
                return res.result();
            }
            throw new TelegramApiException("Telegram sendMessage rejected: " + res.description());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TelegramApiException("Telegram sendMessage network error: " + e.getMessage(), e);
        }
    }

    private String maskChatId(Long chatId) {
        if (chatId == null) return "***";
        String s = chatId.toString();
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }
}
