package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateEquipmentListingRequest(
        @NotBlank(message = "Texnika toifasi koʻrsatilishi shart")
        @Size(max = 50)
        String category,

        @NotBlank(message = "Texnika nomi koʻrsatilishi shart")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Model nomi koʻrsatilishi shart")
        @Size(max = 150)
        String modelName,

        @NotBlank(message = "Ishlab chiqaruvchi koʻrsatilishi shart")
        @Size(max = 150)
        String manufacturer,

        @Min(value = 1970, message = "Ishlab chiqarilgan yil notoʻgʻri")
        Integer yearOfManufacture,

        String condition,

        String description,

        String specifications,

        @NotBlank(message = "Viloyat koʻrsatilishi shart")
        @Size(max = 100)
        String region,

        @NotBlank(message = "Tuman koʻrsatilishi shart")
        @Size(max = 100)
        String district,

        String imageUrls,

        BigDecimal dailyRate,

        @NotNull(message = "Oylik toʻlov koʻrsatilishi shart")
        @DecimalMin(value = "0.01", message = "Oylik toʻlov musbat boʻlishi kerak")
        BigDecimal monthlyRate,

        @NotNull(message = "Texnikaning bozor narxi koʻrsatilishi shart")
        @DecimalMin(value = "0.01", message = "Bozor narxi musbat boʻlishi kerak")
        BigDecimal marketValue,

        String currency,

        BigDecimal minDownPaymentPercent,

        Integer minTermMonths,

        Integer maxTermMonths
) {
}
