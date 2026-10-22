package uz.hosil.platform.farm.service;

import java.math.BigDecimal;

/**
 * Result returned by CropDiseaseInferencePort.
 */
public record InferenceResult(
        String status,
        String conditionName,
        String conditionCode,
        BigDecimal confidenceScore,
        Boolean isHealthy,
        String severity,
        String agronomicAdvice,
        String culturalControls,
        String recommendedChecks,
        String modelName,
        String modelVersion,
        boolean isSimulated,
        String disclaimer,
        String failureReason
) {
    public static InferenceResult failure(String failureReason, String modelName, String modelVersion) {
        return new InferenceResult(
                "FAILED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                modelName,
                modelVersion,
                true,
                null,
                failureReason
        );
    }

    public static InferenceResult unsupported(String reason, String modelName, String modelVersion) {
        return new InferenceResult(
                "UNSUPPORTED",
                "Aniqlab boʻlmadi / Nomaʼlum holat",
                "UNSUPPORTED_IMAGE",
                BigDecimal.valueOf(0.3500),
                false,
                "NONE",
                "Tasvirda aniq oʻsimlik kasalligi belgilari topilmadi yoki tasvir sifati tahlil uchun yetarli emas. Iltimos, bargni tabiiy yorugʻlikda yaqindan, aniq fokusda qayta suratga oling.",
                "1. Kasallangan bargni oʻrtaga qoʻyib suratga oling. 2. Rasm xiralashmaganiga ishonch hosil qiling.",
                "Barg yuzasini vizual tekshirib, shubhali belgilar kuchaysa agronomga murojaat qiling.",
                modelName,
                modelVersion,
                true,
                "DIQQAT: Ushbu tahlil natijasi noaniq deb topildi. Yakuniy xulosa uchun agronom bilan maslahatlashing.",
                reason
        );
    }
}
