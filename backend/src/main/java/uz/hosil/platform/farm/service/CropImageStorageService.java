package uz.hosil.platform.farm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.common.error.ValidationException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service managing secure, private filesystem storage for diagnostic leaf images.
 * Re-encodes images to standard JPEG to strip EXIF/GPS metadata and prevent location leakage.
 */
@Service
public class CropImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(CropImageStorageService.class);

    private final Path rootStoragePath;

    public CropImageStorageService(@Value("${hosil.farm.ai.storage-dir:${user.home}/.hosil/uploads/crop-health}") String storageDir) {
        this.rootStoragePath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStoragePath);
            log.info("Initialized crop diagnostic storage directory at: {}", this.rootStoragePath);
        } catch (IOException e) {
            log.error("Failed to initialize crop diagnostic storage directory: {}", this.rootStoragePath, e);
            throw new IllegalStateException("Could not initialize crop diagnostic storage directory", e);
        }
    }

    /**
     * Re-encodes the image into a clean JPEG, automatically stripping all EXIF, GPS,
     * and hardware metadata, then persists it to private storage.
     */
    public StoredImageResult storeSanitizedImage(UUID diagnosisId, BufferedImage sourceImage) {
        String filename = diagnosisId.toString() + ".jpg";
        Path targetPath = rootStoragePath.resolve(filename).normalize();

        // Enforce boundary check to prevent path traversal
        if (!targetPath.startsWith(rootStoragePath)) {
            throw new ValidationException("Xavfsizlik xatosi: Fayl yoʻli notoʻgʻri (path traversal)");
        }

        // Convert potential ARGB/PNG transparency to RGB with white background before JPEG encoding
        BufferedImage rgbImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g2d = rgbImage.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, sourceImage.getWidth(), sourceImage.getHeight());
            g2d.drawImage(sourceImage, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        try {
            // Writing pure BufferedImage pixels eliminates all camera EXIF/GPS metadata
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpg", baos);
            byte[] jpegBytes = baos.toByteArray();

            Files.write(targetPath, jpegBytes);
            log.info("Stored sanitized diagnostic leaf image for id: {}, size: {} bytes", diagnosisId, jpegBytes.length);

            return new StoredImageResult(targetPath.toString(), "image/jpeg", (long) jpegBytes.length, jpegBytes);
        } catch (IOException e) {
            log.error("Failed to store sanitized image for diagnosis {}", diagnosisId, e);
            throw new IllegalStateException("Rasmni saqlashda xatolik yuz berdi: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the sanitized image bytes from private storage after verifying authorization.
     */
    public byte[] readImageBytes(String storagePath) {
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!path.startsWith(rootStoragePath)) {
            throw new ValidationException("Ruxsatsiz fayl soʻrovi");
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Rasm fayli topilmadi");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Rasmni oʻqishda xatolik: " + e.getMessage(), e);
        }
    }

    /**
     * Safely deletes stored image when diagnosis is purged or deleted.
     */
    public void deleteImage(String storagePath) {
        try {
            Path path = Paths.get(storagePath).toAbsolutePath().normalize();
            if (path.startsWith(rootStoragePath) && Files.exists(path)) {
                Files.delete(path);
                log.info("Deleted diagnostic image file: {}", storagePath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete diagnostic image: {}", storagePath, e);
        }
    }

    public record StoredImageResult(
            String storagePath,
            String contentType,
            Long fileSize,
            byte[] sanitizedBytes
    ) {}
}
