package uz.hosil.platform.farm.service;

import java.util.UUID;

/**
 * Request payload dispatched to CropDiseaseInferencePort.
 */
public record InferenceRequest(
        UUID diagnosisId,
        String cropType,
        byte[] imageBytes,
        String userNotes
) {}
