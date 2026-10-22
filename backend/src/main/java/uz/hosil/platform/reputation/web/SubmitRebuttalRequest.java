package uz.hosil.platform.reputation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitRebuttalRequest(
        @NotBlank(message = "responseText cannot be blank")
        @Size(max = 500, message = "responseText cannot exceed 500 characters")
        String responseText
) {
}
