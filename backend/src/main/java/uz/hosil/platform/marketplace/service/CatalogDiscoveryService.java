package uz.hosil.platform.marketplace.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.identity.IdentityLookupService;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryAllowedUnitRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CatalogProductDetailDto;
import uz.hosil.platform.marketplace.web.CatalogProductSummaryDto;
import uz.hosil.platform.marketplace.web.CategoryDto;
import uz.hosil.platform.marketplace.web.CreateCatalogProductRequest;
import uz.hosil.platform.marketplace.web.PageEnvelope;
import uz.hosil.platform.marketplace.web.PublicSellerOfferDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for public agricultural-input catalog discovery, category taxonomy, and product offers (MKT-001, MKT-002).
 */
@Service
@Transactional(readOnly = true)
public class CatalogDiscoveryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAllowedUnitRepository categoryAllowedUnitRepository;
    private final CatalogProductRepository catalogProductRepository;
    private final SellerOfferRepository sellerOfferRepository;
    private final IdentityLookupService identityLookupService;

    public CatalogDiscoveryService(
            CategoryRepository categoryRepository,
            CategoryAllowedUnitRepository categoryAllowedUnitRepository,
            CatalogProductRepository catalogProductRepository,
            SellerOfferRepository sellerOfferRepository,
            IdentityLookupService identityLookupService
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryAllowedUnitRepository = categoryAllowedUnitRepository;
        this.catalogProductRepository = catalogProductRepository;
        this.sellerOfferRepository = sellerOfferRepository;
        this.identityLookupService = identityLookupService;
    }

    /**
     * Lists active product categories with optional categoryType filter.
     */
    public List<CategoryDto> listCategories(String categoryType) {
        List<CategoryEntity> entities;
        if (categoryType != null && !categoryType.isBlank()) {
            entities = categoryRepository.findByCategoryTypeAndIsActiveTrueOrderBySortOrderAsc(categoryType.trim().toUpperCase());
        } else {
            entities = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
        }
        return entities.stream().map(CategoryDto::fromEntity).toList();
    }

    /**
     * Browses active standardized catalog products with optional category and search filters.
     */
    public PageEnvelope<CatalogProductSummaryDto> listProducts(UUID categoryId, String search, Pageable pageable) {
        Specification<CatalogProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("isActive")));

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nameUz")), term),
                        cb.like(cb.lower(root.get("nameRu")), term),
                        cb.like(cb.lower(root.get("brandName")), term),
                        cb.like(cb.lower(root.get("activeSubstance")), term)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CatalogProductEntity> page = catalogProductRepository.findAll(spec, pageable);

        Map<UUID, CategoryEntity> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        Page<CatalogProductSummaryDto> dtoPage = page.map(product -> {
            CategoryEntity cat = categoryMap.get(product.getCategoryId());
            String catNameUz = cat != null ? cat.getUzName() : null;
            String catNameRu = cat != null ? cat.getRuName() : null;
            long activeCount = sellerOfferRepository.countActiveOffersByProductId(product.getId());
            BigDecimal minPrice = sellerOfferRepository.findMinPriceByProductId(product.getId());
            BigDecimal maxPrice = sellerOfferRepository.findMaxPriceByProductId(product.getId());
            return CatalogProductSummaryDto.fromEntity(
                    product,
                    catNameUz,
                    catNameRu,
                    activeCount,
                    minPrice,
                    maxPrice,
                    "UZS"
            );
        });

        return PageEnvelope.from(dtoPage);
    }

    /**
     * Retrieves detailed master catalog product information.
     */
    public CatalogProductDetailDto getProductDetail(UUID productId) {
        CatalogProductEntity product = catalogProductRepository.findById(productId)
                .filter(CatalogProductEntity::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("Katalog mahsuloti topilmadi: " + productId));

        CategoryEntity cat = categoryRepository.findById(product.getCategoryId()).orElse(null);
        String catCode = cat != null ? cat.getCode() : null;
        String catNameUz = cat != null ? cat.getUzName() : null;
        String catNameRu = cat != null ? cat.getRuName() : null;

        long activeCount = sellerOfferRepository.countActiveOffersByProductId(product.getId());
        BigDecimal minPrice = sellerOfferRepository.findMinPriceByProductId(product.getId());
        BigDecimal maxPrice = sellerOfferRepository.findMaxPriceByProductId(product.getId());

        return CatalogProductDetailDto.fromEntity(
                product,
                catCode,
                catNameUz,
                catNameRu,
                activeCount,
                minPrice,
                maxPrice,
                "UZS"
        );
    }

    /**
     * Lists active commercial seller offers for a catalog product (safe public discovery).
     */
    public PageEnvelope<PublicSellerOfferDto> listProductOffers(
            UUID productId,
            String region,
            String unitCode,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            Pageable pageable
    ) {
        if (!catalogProductRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Katalog mahsuloti topilmadi: " + productId);
        }

        Specification<SellerOfferEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("catalogProductId"), productId));
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            predicates.add(cb.gt(root.get("availableQuantity"), BigDecimal.ZERO));

            if (region != null && !region.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("region")), region.trim().toLowerCase()));
            }

            if (unitCode != null && !unitCode.isBlank()) {
                predicates.add(cb.equal(root.get("unitCode"), unitCode.trim().toUpperCase()));
            }

            if (minPrice != null) {
                predicates.add(cb.ge(root.get("unitPrice"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.le(root.get("unitPrice"), maxPrice));
            }

            if (currency != null && !currency.isBlank()) {
                predicates.add(cb.equal(root.get("currency"), currency.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SellerOfferEntity> page = sellerOfferRepository.findAll(spec, pageable);

        Page<PublicSellerOfferDto> dtoPage = page.map(offer -> {
            String businessName = identityLookupService.findSellerProfile(offer.getSellerUserId())
                    .map(profile -> profile.businessName())
                    .orElseGet(() -> identityLookupService.findUserById(offer.getSellerUserId())
                            .map(u -> u.fullName())
                            .orElse("Tasdiqlangan taʼminotchi"));
            return PublicSellerOfferDto.fromEntity(offer, businessName);
        });

        return PageEnvelope.from(dtoPage);
    }

    /**
     * Administrator creates a new standardized master catalog product.
     */
    @Transactional
    public CatalogProductDetailDto createCatalogProduct(CreateCatalogProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Toifa topilmadi: " + request.categoryId()));

        boolean allowed = categoryAllowedUnitRepository.existsByIdCategoryIdAndIdUnitCode(
                category.getId(), request.defaultUnitCode().trim().toUpperCase()
        );
        if (!allowed) {
            throw new BusinessConflictException("Birlamchi oʻlchov birligi ushbu toifa uchun ruxsat etilmagan: " + request.defaultUnitCode());
        }

        CatalogProductEntity entity = new CatalogProductEntity(
                UUID.randomUUID(),
                category.getId(),
                request.nameUz().trim(),
                request.nameRu().trim(),
                request.brandName() != null ? request.brandName().trim() : null,
                request.activeSubstance() != null ? request.activeSubstance().trim() : null,
                request.defaultUnitCode().trim().toUpperCase(),
                request.description(),
                true
        );

        CatalogProductEntity saved = catalogProductRepository.save(entity);

        return CatalogProductDetailDto.fromEntity(
                saved,
                category.getCode(),
                category.getUzName(),
                category.getRuName(),
                0,
                null,
                null,
                "UZS"
        );
    }
}
