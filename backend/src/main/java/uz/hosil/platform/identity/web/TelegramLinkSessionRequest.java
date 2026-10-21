package uz.hosil.platform.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TelegramLinkSessionRequest(
        @NotBlank(message = "Telefon raqami kiritilishi shart")
        @Pattern(regexp = "^(\\+998)[0-9]{9}$", message = "Telefon raqami E.164 formatida (+998XXXXXXXXX) boʻlishi kerak")
        String phoneNumber
) {}
