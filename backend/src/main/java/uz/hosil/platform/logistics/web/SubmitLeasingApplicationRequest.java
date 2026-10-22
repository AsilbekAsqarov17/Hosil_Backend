package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitLeasingApplicationRequest(
        @NotNull(message = "Texnika identifikatori koʻrsatilishi shart")
        UUID equipmentId,

        UUID farmId,

        String applicantBusinessType,

        @NotBlank(message = "Ariza beruvchi ismi-sharifi koʻrsatilishi shart")
        @Size(max = 150)
        String applicantName,

        @NotBlank(message = "Telefon raqami koʻrsatilishi shart")
        @Size(max = 50)
        String applicantPhone,

        @NotNull(message = "Soʻralayotgan lizing muddati koʻrsatilishi shart")
        @Min(value = 6, message = "Lizing muddati kamida 6 oy boʻlishi kerak")
        Integer desiredTermMonths,

        @NotNull(message = "Boshlangʻich toʻlov miqdori koʻrsatilishi shart")
        @DecimalMin(value = "0.00", message = "Boshlangʻich toʻlov manfiy boʻlishi mumkin emas")
        BigDecimal proposedDownPayment,

        String intendedUseNotes,

        String idempotencyKey
) {
}
