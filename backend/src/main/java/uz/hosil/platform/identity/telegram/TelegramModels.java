package uz.hosil.platform.identity.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class TelegramModels {

    private TelegramModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(
            @JsonProperty("id") Long id,
            @JsonProperty("is_bot") Boolean isBot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("username") String username
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(
            @JsonProperty("id") Long id,
            @JsonProperty("type") String type,
            @JsonProperty("title") String title,
            @JsonProperty("username") String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramContact(
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("user_id") Long userId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") Long messageId,
            @JsonProperty("from") TelegramUser from,
            @JsonProperty("chat") TelegramChat chat,
            @JsonProperty("date") Long date,
            @JsonProperty("text") String text,
            @JsonProperty("contact") TelegramContact contact
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUpdate(
            @JsonProperty("update_id") Long updateId,
            @JsonProperty("message") TelegramMessage message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramGetUpdatesResponse(
            @JsonProperty("ok") Boolean ok,
            @JsonProperty("description") String description,
            @JsonProperty("result") List<TelegramUpdate> result
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramGetMeResponse(
            @JsonProperty("ok") Boolean ok,
            @JsonProperty("description") String description,
            @JsonProperty("result") TelegramUser result
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramSendMessageResponse(
            @JsonProperty("ok") Boolean ok,
            @JsonProperty("description") String description,
            @JsonProperty("result") TelegramMessage result
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TelegramKeyboardButton(
            @JsonProperty("text") String text,
            @JsonProperty("request_contact") Boolean requestContact
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TelegramReplyKeyboardMarkup(
            @JsonProperty("keyboard") List<List<TelegramKeyboardButton>> keyboard,
            @JsonProperty("resize_keyboard") Boolean resizeKeyboard,
            @JsonProperty("one_time_keyboard") Boolean oneTimeKeyboard
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TelegramReplyKeyboardRemove(
            @JsonProperty("remove_keyboard") Boolean removeKeyboard
    ) {
        public TelegramReplyKeyboardRemove() {
            this(true);
        }
    }
}
