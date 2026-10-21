package uz.hosil.platform.marketplace.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.marketplace.service.CatalogDiscoveryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Controller exposing public agricultural-input catalog discovery and admin catalog management endpoints.
 */
@RestController
@RequestMapping("/api/v1/market/catalog")
public class CatalogDiscoveryController {

    private final CatalogDiscoveryService catalogDiscoveryService;

    public CatalogDiscoveryController(CatalogDiscoveryService catalogDiscoveryService) {
        this.catalogDiscoveryService = catalogDiscoveryService;
    }

    /**
     * Lists product categories with optional categoryType filter.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> listCategories(
            @RequestParam(required = false) String categoryType
    ) {
        List<CategoryDto> categories = catalogDiscoveryService.listCategories(categoryType);
        return ResponseEntity.ok(categories);
    }

    /**
     * Browses active master catalog products with optional category and search filters.
     */
    @GetMapping("/products")
    public ResponseEntity<PageEnvelope<CatalogProductSummaryDto>> listProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<CatalogProductSummaryDto> page = catalogDiscoveryService.listProducts(
                categoryId, search, pageable
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieves master catalog product details.
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<CatalogProductDetailDto> getProductDetail(
            @PathVariable UUID productId
    ) {
        CatalogProductDetailDto detail = catalogDiscoveryService.getProductDetail(productId);
        return ResponseEntity.ok(detail);
    }

    /**
     * Lists active commercial seller offers for a catalog product.
     */
    @GetMapping("/products/{productId}/offers")
    public ResponseEntity<PageEnvelope<PublicSellerOfferDto>> listProductOffers(
            @PathVariable UUID productId,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String unitCode,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String currency,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageEnvelope<PublicSellerOfferDto> page = catalogDiscoveryService.listProductOffers(
                productId, region, unitCode, minPrice, maxPrice, currency, pageable
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Administrator creates a new master catalog product entry.
     */
    @PostMapping("/products")
    public ResponseEntity<CatalogProductDetailDto> createCatalogProduct(
            @Valid @RequestBody CreateCatalogProductRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null || (!principal.hasRole("ADMINISTRATOR")
                && !principal.hasRole("ADMIN_OPERATOR")
                && !principal.hasRole("ADMIN_SUPERVISOR"))) {
            throw new AccessDeniedException("Faqat platforma maʼmurlari yangi katalog mahsuloti qoʻsha oladi");
        }

        CatalogProductDetailDto detail = catalogDiscoveryService.createCatalogProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }
}
