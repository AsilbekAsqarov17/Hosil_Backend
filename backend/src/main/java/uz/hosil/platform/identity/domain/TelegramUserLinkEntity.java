package uz.hosil.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores verified association between an authoritative phone number and a Telegram chat.
 * Established exclusively after contact sharing verification per Milestone 6C.5 and DEC-003.
 */
@Entity
@Table(name = "telegram_user_links")
public class TelegramUserLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TelegramUserLinkEntity() {
        // JPA standard constructor
    }

    public TelegramUserLinkEntity(
            String phoneNumber,
            Long telegramChatId,
            Long telegramUserId,
            String telegramUsername,
            String firstName,
            String lastName
    ) {
        this.phoneNumber = phoneNumber;
        this.telegramChatId = telegramChatId;
        this.telegramUserId = telegramUserId;
        this.telegramUsername = telegramUsername;
        this.firstName = firstName;
        this.lastName = lastName;
        this.linkedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public void setTelegramUsername(String telegramUsername) {
        this.telegramUsername = telegramUsername;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
