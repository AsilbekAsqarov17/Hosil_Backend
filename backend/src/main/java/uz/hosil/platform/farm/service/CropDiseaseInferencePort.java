package uz.hosil.platform.farm.service;

/**
 * Provider-neutral interface for crop disease diagnostic inference.
 * Conforms to docs/01_prd.md AI-006 and docs/02_architecture.md §8.4.
 */
public interface CropDiseaseInferencePort {

    /**
     * Executes diagnostic inference for a given crop leaf image.
     */
    InferenceResult inferCropDisease(InferenceRequest request);
}
