package uz.hosil.platform.farm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.hosil.platform.common.error.ValidationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Validates diagnostic crop leaf images: size limits, magic bytes, and dimensions.
 * Conforms to docs/01_prd.md MEDIA-003 and AI-004.
 */
@Component
public class CropImageValidator {

    private final long maxSizeBytes;
    private final int minDimension;
    private final int maxDimension;

    public CropImageValidator(
            @Value("${hosil.farm.ai.max-image-size-bytes:10485760}") long maxSizeBytes,
            @Value("${hosil.farm.ai.min-dimension:200}") int minDimension,
            @Value("${hosil.farm.ai.max-dimension:4096}") int maxDimension) {
        this.maxSizeBytes = maxSizeBytes;
        this.minDimension = minDimension;
        this.maxDimension = maxDimension;
    }

    public record ValidatedImage(
            BufferedImage bufferedImage,
            String detectedMimeType,
            int width,
            int height,
            long sizeBytes
    ) {}

    public ValidatedImage validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("Rasm fayli boʻsh boʻlishi mumkin emas");
        }

        if (bytes.length < 100) {
            throw new ValidationException("Rasm fayli juda kichik yoki shikastlangan");
        }

        if (bytes.length > maxSizeBytes) {
            throw new ValidationException(String.format(
                    "Rasm hajmi ruxsat etilgan maksimal chegaradan (%d MB) oshib ketdi",
                    maxSizeBytes / (1024 * 1024)
            ));
        }

        String detectedMimeType = detectMimeType(bytes);
        if (detectedMimeType == null) {
            throw new ValidationException("Qoʻllab-quvvatlanmaydigan rasm formati. Faqat JPEG, PNG va WebP formatlari qabul qilinadi");
        }

        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new ValidationException("Rasm faylini oʻqishda xatolik yuz berdi: " + e.getMessage(), e);
        }

        if (bufferedImage == null) {
            throw new ValidationException("Yaroqsiz yoki shikastlangan rasm fayli (dekodlash imkoni boʻlmadi)");
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        if (width < minDimension || height < minDimension) {
            throw new ValidationException(String.format(
                    "Rasm oʻlchami juda kichik: %dx%d (minimal talab: %dx%d piksel)",
                    width, height, minDimension, minDimension
            ));
        }

        if (width > maxDimension || height > maxDimension) {
            throw new ValidationException(String.format(
                    "Rasm oʻlchami ruxsat etilgan maksimal chegaradan oshdi: %dx%d (maksimal talab: %dx%d piksel)",
                    width, height, maxDimension, maxDimension
            ));
        }

        return new ValidatedImage(bufferedImage, detectedMimeType, width, height, bytes.length);
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length < 12) return null;

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 &&
            bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }

        // WebP: RIFF .... WEBP
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
            bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }

        return null;
    }
}
