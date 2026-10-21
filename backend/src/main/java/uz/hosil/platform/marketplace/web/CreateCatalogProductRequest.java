package uz.hosil.platform.marketplace.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for administrator creating a standardized master catalog product.
 */
public record CreateCatalogProductRequest(
        @NotNull(message = "Kategoriya kiritilishi shart")
        UUID categoryId,

        @NotBlank(message = "Oʻzbekcha mahsulot nomi kiritilishi shart")
        String nameUz,

        @NotBlank(message = "Ruscha mahsulot nomi kiritilishi shart")
        String nameRu,

        String brandName,

        String activeSubstance,

        @NotBlank(message = "Birlamchi oʻlchov birligi kiritilishi shart")
        String defaultUnitCode,

        String description
) {}
