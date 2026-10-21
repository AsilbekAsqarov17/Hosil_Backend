package uz.hosil.platform.identity.telegram;

import java.util.List;

/**
 * Client interface for interacting with the official Telegram Bot API.
 * Mockable for deterministic testing without external network calls.
 */
public interface TelegramApiClient {

    TelegramModels.TelegramUser getMe();

    List<TelegramModels.TelegramUpdate> getUpdates(Long offset, Integer limit, Integer timeoutSeconds);

    TelegramModels.TelegramMessage sendMessage(Long chatId, String text, String parseMode, Object replyMarkup);
}
