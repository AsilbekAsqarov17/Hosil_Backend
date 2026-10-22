package uz.hosil.platform.farm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Deterministic sandbox inference provider for development, integration, and staging testing.
 * Conforms to docs/01_prd.md AI-003, AI-004, AI-007.
 * All results produced by this gateway are explicitly marked with isSimulated = true.
 */
@Component
public class MockSandboxCropDiseaseInferenceGateway implements CropDiseaseInferencePort {

    private static final Logger log = LoggerFactory.getLogger(MockSandboxCropDiseaseInferenceGateway.class);

    public static final String STANDARD_AGRONOMIC_DISCLAIMER =
            "Ushbu tashxis sunʼiy intellekt dastlabki tavsiyasi hisoblanadi. " +
            "Muhim agrotexnik va kimyoviy ishlov berish qarorlarini qabul qilishdan oldin " +
            "sertifikatlangan agronom bilan maslahatlashing.";

    private final String modelName;
    private final String modelVersion;

    private volatile boolean simulatedFailure = false;
    private volatile boolean simulatedUnsupported = false;
    private volatile BigDecimal simulatedConfidence = null;

    public MockSandboxCropDiseaseInferenceGateway(
            @Value("${hosil.farm.ai.model-name:Hosil-CropVision-Sandbox}") String modelName,
            @Value("${hosil.farm.ai.model-version:1.0.0-dev}") String modelVersion) {
        this.modelName = modelName;
        this.modelVersion = modelVersion;
    }

    @Override
    public InferenceResult inferCropDisease(InferenceRequest request) {
        log.info("Processing crop disease inference for diagnosisId: {}, cropType: {}, bytes: {}",
                request.diagnosisId(), request.cropType(), request.imageBytes() != null ? request.imageBytes().length : 0);

        if (simulatedFailure) {
            log.warn("MockSandboxGateway simulated failure triggered for diagnosisId: {}", request.diagnosisId());
            return InferenceResult.failure("AI tahlil xizmati vaqtincha javob bermadi (simulyatsiya qilingan nosozlik)", modelName, modelVersion);
        }

        if (simulatedUnsupported) {
            log.info("MockSandboxGateway simulated unsupported outcome triggered for diagnosisId: {}", request.diagnosisId());
            return InferenceResult.unsupported("Tasvir sifati past yoki ekin turi noaniq", modelName, modelVersion);
        }

        String normalizedCrop = request.cropType() != null ? request.cropType().trim().toUpperCase(Locale.ROOT) : "";
        String notes = request.userNotes() != null ? request.userNotes().toLowerCase(Locale.ROOT) : "";

        // Check for healthy simulation trigger
        boolean wantsHealthy = notes.contains("sog'lom") || notes.contains("soglom") || notes.contains("healthy");

        BigDecimal confidence = simulatedConfidence != null
                ? simulatedConfidence
                : BigDecimal.valueOf(0.9250).setScale(4, RoundingMode.HALF_UP);

        if (normalizedCrop.contains("TOMATO") || normalizedCrop.contains("POMIDOR")) {
            if (wantsHealthy) {
                return new InferenceResult(
                        "COMPLETED",
                        "Sogʻlom pomidor bargi",
                        "TOMATO_HEALTHY",
                        confidence,
                        true,
                        "NONE",
                        "Barg toʻqimalari bir tekis yashil va kasallik alomatlarisiz. Agrotexnik parvarish rejimini davom ettiring.",
                        "1. Rejali tomchilatib sugʻorishni davom ettiring. 2. Muntazam profilaktik monitoring olib boring.",
                        "Barg osti va oʻsish nuqtalarini haftada bir marta koʻzdan kechiring.",
                        modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
                );
            }
            if (notes.contains("alternarioz") || notes.contains("early")) {
                return new InferenceResult(
                        "COMPLETED",
                        "Alternarioz (Erta qora dogʻlanish)",
                        "TOMATO_EARLY_BLIGHT",
                        confidence,
                        false,
                        "MEDIUM",
                        "Quyi barglarda konsentrik aylana shaklidagi qoramtir-jigarrang dogʻlar va sariq hoshiya kuzatiladi.",
                        "1. Ekin qoldiqlarini maydondan tozalang. 2. Almashlab ekish qoidasiga rioya qiling. 3. Mis tarkibli biologik preparatlar bilan profilaktik ishlov bering.",
                        "Dogʻlarning konsentrik nishon shaklida ekanligiga eʼtibor bering.",
                        modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
                );
            }
            return new InferenceResult(
                    "COMPLETED",
                    "Fitoftoroz (Kechki chirish / Late Blight)",
                    "TOMATO_LATE_BLIGHT",
                    confidence,
                    false,
                    "HIGH",
                    "Barglarda jigarrang-qoramtir suvli dogʻlar va nam havoda pastki qismida oqish mogʻor tolalari paydo boʻlishi fitoftorozga xos. Yuqori namlikda tez tarqaladi.",
                    "1. Tomchilatib sugʻorishga oʻting, barglarni namlantirishdan saqlaning. 2. Zararlangan barg va poyalarni zudlik bilan kesib olib, maydondan tashqarida yoʻqoting. 3. Issiqxonada havo aylanishini kuchaytiring.",
                    "Bargning orqa tomonida oq mogʻor tolalari bor-yoʻqligini tekshiring. Mevalarda qattiq jigarrang dogʻlar mavjudligini koʻzdan kechiring.",
                    modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
            );
        }

        if (normalizedCrop.contains("COTTON") || normalizedCrop.contains("PAXTA")) {
            if (wantsHealthy) {
                return new InferenceResult(
                        "COMPLETED",
                        "Sogʻlom gʻoʻza bargi",
                        "COTTON_HEALTHY",
                        confidence,
                        true,
                        "NONE",
                        "Gʻoʻza barglari toza, oʻsish surʼati meʼyorda. Standart agrotexnika qoidalariga rioya qiling.",
                        "Qator oralariga oʻz vaqtida kultivatsiya ishlovini bering.",
                        "Shira va oʻrgimchakkana tushmaganligini tekshirish uchun barg ostini koʻzdan kechiring.",
                        modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
                );
            }
            return new InferenceResult(
                    "COMPLETED",
                    "Paxta vilti (Vertitsillyoz vilt)",
                    "COTTON_VERTICILLIUM_WILT",
                    confidence,
                    false,
                    "CRITICAL",
                    "Barglarda tomirlar oraligʻida sargʻish-jigarrang mozaikali qurish ('yoʻlbars terisi' belgisi). Poyasi kesilganda oʻtkazuvchi toʻqimalarning qorayishi koʻrinadi.",
                    "1. Tuproq aeratsiyasini yaxshilang. 2. Fosfor-kaliyli oʻgʻitlar balansini taʼminlang. 3. Kuchli zararlangan turlarni maydondan chiqarib tashlang.",
                    "Poya pastki qismini koʻndalang kesib, oʻtkazuvchi naylarning jigarrang halqa hosil qilganini tekshiring.",
                    modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
            );
        }

        if (normalizedCrop.contains("WHEAT") || normalizedCrop.contains("BUG'DOY") || normalizedCrop.contains("BUGDOY")) {
            return new InferenceResult(
                    "COMPLETED",
                    "Sariq zang (Puccinia striiformis)",
                    "WHEAT_YELLOW_RUST",
                    confidence,
                    false,
                    "HIGH",
                    "Barglarda chiziqli joylashgan sariq-toʻq sariq changsimon sporalar pustulalari kuzatiladi.",
                    "1. Chidamli navlarni tanlang. 2. Haddan tashqari koʻp azotli oʻgʻit berishdan saqlaning. 3. Profilaktik fungitsid purkash muddatlarini belgilang.",
                    "Bargni oq qogʻozga ishqalaganda sariq chang izi qolishini tekshiring.",
                    modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
            );
        }

        if (normalizedCrop.contains("APPLE") || normalizedCrop.contains("OLMA")) {
            return new InferenceResult(
                    "COMPLETED",
                    "Olma qoʻtiri (Venturia inaequalis)",
                    "APPLE_SCAB",
                    confidence,
                    false,
                    "MEDIUM",
                    "Barglar va yosh mevalarda zaytun-qoramtir baxmalli dogʻlar paydo boʻladi.",
                    "1. Bogʻda toʻkilgan barglarni yigʻishtirib yoʻqoting. 2. Daraxt tojini yorugʻlik va havo yaxshi oʻtadigan darajada butang.",
                    "Barg orqasi va meva bandini koʻzdan kechiring.",
                    modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
            );
        }

        if (normalizedCrop.contains("GRAPE") || normalizedCrop.contains("UZUM")) {
            return new InferenceResult(
                    "COMPLETED",
                    "Uzum mildyusi (Soxta un-shudring)",
                    "GRAPE_DOWNY_MILDEW",
                    confidence,
                    false,
                    "HIGH",
                    "Barg ustida yogʻsimon sariq dogʻlar, sernam havoda pastki tomonida oqish gʻubor paydo boʻladi.",
                    "1. Tok novdalarini simbagʻazga koʻtarib bogʻlang. 2. Qalinlashib ketgan barglarni siyraklating.",
                    "Bargning ostki qismidagi oq gʻubor qatlamini tekshiring.",
                    modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
            );
        }

        // Generic fallback for any other crop or unsupported crop
        if (normalizedCrop.isBlank() || normalizedCrop.contains("UNKNOWN") || normalizedCrop.contains("NON_PLANT")) {
            return InferenceResult.unsupported("Tasvirda ekin bargi aniqlanmadi yoki ekin turi koʻrsatilmadi", modelName, modelVersion);
        }

        // Default crop classification for other supported crops (e.g. Potato, Pepper, Peach)
        return new InferenceResult(
                "COMPLETED",
                "Fitozang va dogʻlanish alomatlari (" + request.cropType() + ")",
                normalizedCrop + "_SPOTTING",
                confidence,
                false,
                "LOW",
                "Barg yuzasida dastlabki nuqtali dogʻlanishlar kuzatilmoqda. Namlik rejimini nazorat qiling.",
                "1. Oʻsimlik qator oralarini begona oʻtlardan tozalang. 2. Oziqlantirish balansiga eʼtibor bering.",
                "Barglar rangining keyingi oʻzgarishini kuzating.",
                modelName, modelVersion, true, STANDARD_AGRONOMIC_DISCLAIMER, null
        );
    }

    // Testing simulation mutators
    public void setSimulatedFailure(boolean simulatedFailure) {
        this.simulatedFailure = simulatedFailure;
    }

    public void setSimulatedUnsupported(boolean simulatedUnsupported) {
        this.simulatedUnsupported = simulatedUnsupported;
    }

    public void setSimulatedConfidence(BigDecimal simulatedConfidence) {
        this.simulatedConfidence = simulatedConfidence;
    }

    public void reset() {
        this.simulatedFailure = false;
        this.simulatedUnsupported = false;
        this.simulatedConfidence = null;
    }
}
