package uz.hosil.platform.marketplace.service;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.identity.IdentityLookupService;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryAllowedUnitRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateSellerOfferRequest;
import uz.hosil.platform.marketplace.web.PageEnvelope;
import uz.hosil.platform.marketplace.web.SellerOfferDetailDto;
import uz.hosil.platform.marketplace.web.UpdateSellerOfferRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for commercial input suppliers managing their product offers (DEC-004, DEC-005, DEC-009, MKT-003).
 */
@Service
@Transactional
public class SellerOfferService {

    private static final Logger log = LoggerFactory.getLogger(SellerOfferService.class);

    private final SellerOfferRepository sellerOfferRepository;
    private final CatalogProductRepository catalogProductRepository;
    private final CategoryAllowedUnitRepository categoryAllowedUnitRepository;
    private final IdentityLookupService identityLookupService;

    public SellerOfferService(
            SellerOfferRepository sellerOfferRepository,
            CatalogProductRepository catalogProductRepository,
            CategoryAllowedUnitRepository categoryAllowedUnitRepository,
            IdentityLookupService identityLookupService
    ) {
        this.sellerOfferRepository = sellerOfferRepository;
        this.catalogProductRepository = catalogProductRepository;
        this.categoryAllowedUnitRepository = categoryAllowedUnitRepository;
        this.identityLookupService = identityLookupService;
    }

    /**
     * Verified commercial supplier creates an offer on a catalog product.
     */
    public SellerOfferDetailDto createOffer(CreateSellerOfferRequest request, UUID sellerUserId) {
        // 1. Seller Eligibility & Verification Check (DEC-004)
        if (!identityLookupService.isVerifiedCommercialSupplier(sellerUserId)) {
            log.warn("Unverified supplier {} attempted to create an offer", sellerUserId);
            throw new AccessDeniedException("Faqat tasdiqlangan tijorat taʼminotchilari taklif kirita oladi (DEC-004)");
        }

        // 2. Catalog Product existence and active check
        CatalogProductEntity product = catalogProductRepository.findById(request.catalogProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Katalog mahsuloti topilmadi: " + request.catalogProductId()));

        if (!product.getIsActive()) {
            throw new BusinessConflictException("Katalog mahsuloti nofaol holatda");
        }

        // 3. Unit compatibility check (DEC-009, Table 15)
        String unitCode = request.unitCode().trim().toUpperCase();
        boolean unitAllowed = categoryAllowedUnitRepository.existsByIdCategoryIdAndIdUnitCode(product.getCategoryId(), unitCode);
        if (!unitAllowed) {
            throw new BusinessConflictException("Ushbu oʻlchov birligi tanlangan mahsulot toifasi uchun ruxsat etilmagan: " + unitCode);
        }

        // 4. Commercial parameter validations
        if (request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Narx noldan katta boʻlishi kerak");
        }

        if (request.availableQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessConflictException("Mavjud miqdor manfiy boʻlishi mumkin emas");
        }

        BigDecimal moq = request.minOrderQuantity() != null ? request.minOrderQuantity() : BigDecimal.ONE;
        if (moq.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Minimal buyurtma miqdori noldan katta boʻlishi kerak");
        }

        BigDecimal step = request.stepQuantity() != null ? request.stepQuantity() : BigDecimal.ONE;
        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Qadam miqdori noldan katta boʻlishi kerak");
        }

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().trim().toUpperCase()
                : "UZS";

        SellerOfferEntity offer = new SellerOfferEntity(
                UUID.randomUUID(),
                sellerUserId,
                product.getId(),
                request.unitPrice(),
                currency,
                unitCode,
                request.availableQuantity(),
                BigDecimal.ZERO,
                moq,
                step,
                request.region() != null ? request.region().trim() : null,
                request.district() != null ? request.district().trim() : null,
                request.notes(),
                "ACTIVE"
        );

        SellerOfferEntity saved = sellerOfferRepository.save(offer);
        log.info("Seller offer created: id={}, seller={}, product={}, price={}", saved.getId(), sellerUserId, product.getId(), saved.getUnitPrice());

        return SellerOfferDetailDto.fromEntity(
                saved,
                product.getNameUz(),
                product.getNameRu(),
                product.getBrandName()
        );
    }

    /**
     * Lists offers belonging strictly to the authenticated supplier.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<SellerOfferDetailDto> getMyOffers(
            String status,
            UUID catalogProductId,
            Pageable pageable,
            UUID sellerUserId
    ) {
        Specification<SellerOfferEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("sellerUserId"), sellerUserId));

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }

            if (catalogProductId != null) {
                predicates.add(cb.equal(root.get("catalogProductId"), catalogProductId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SellerOfferEntity> page = sellerOfferRepository.findAll(spec, pageable);

        Map<UUID, CatalogProductEntity> productMap = catalogProductRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogProductEntity::getId, p -> p));

        Page<SellerOfferDetailDto> dtoPage = page.map(offer -> {
            CatalogProductEntity prod = productMap.get(offer.getCatalogProductId());
            String nameUz = prod != null ? prod.getNameUz() : null;
            String nameRu = prod != null ? prod.getNameRu() : null;
            String brand = prod != null ? prod.getBrandName() : null;
            return SellerOfferDetailDto.fromEntity(offer, nameUz, nameRu, brand);
        });

        return PageEnvelope.from(dtoPage);
    }

    /**
     * Retrieves specific offer detail with strict ownership verification.
     */
    @Transactional(readOnly = true)
    public SellerOfferDetailDto getMyOfferDetail(UUID offerId, UUID sellerUserId) {
        SellerOfferEntity offer = sellerOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Taklif topilmadi: " + offerId));

        if (!offer.getSellerUserId().equals(sellerUserId)) {
            throw new AccessDeniedException("Faqat taklif egasi ushbu taklifni koʻra oladi");
        }

        CatalogProductEntity prod = catalogProductRepository.findById(offer.getCatalogProductId()).orElse(null);
        String nameUz = prod != null ? prod.getNameUz() : null;
        String nameRu = prod != null ? prod.getNameRu() : null;
        String brand = prod != null ? prod.getBrandName() : null;

        return SellerOfferDetailDto.fromEntity(offer, nameUz, nameRu, brand);
    }

    /**
     * Updates permitted commercial fields on seller offer.
     */
    public SellerOfferDetailDto updateOffer(UUID offerId, UpdateSellerOfferRequest request, UUID sellerUserId) {
        SellerOfferEntity offer = sellerOfferRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Taklif topilmadi: " + offerId));

        if (!offer.getSellerUserId().equals(sellerUserId)) {
            throw new AccessDeniedException("Faqat taklif egasi ushbu taklifni tahrirlay oladi");
        }

        CatalogProductEntity product = catalogProductRepository.findById(offer.getCatalogProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Katalog mahsuloti topilmadi: " + offer.getCatalogProductId()));

        if (request.unitCode() != null && !request.unitCode().isBlank()) {
            String newUnit = request.unitCode().trim().toUpperCase();
            boolean unitAllowed = categoryAllowedUnitRepository.existsByIdCategoryIdAndIdUnitCode(product.getCategoryId(), newUnit);
            if (!unitAllowed) {
                throw new BusinessConflictException("Ushbu oʻlchov birligi tanlangan mahsulot toifasi uchun ruxsat etilmagan: " + newUnit);
            }
            offer.setUnitCode(newUnit);
        }

        if (request.unitPrice() != null) {
            if (request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessConflictException("Narx noldan katta boʻlishi kerak");
            }
            offer.setUnitPrice(request.unitPrice());
        }

        if (request.availableQuantity() != null) {
            if (request.availableQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessConflictException("Mavjud miqdor manfiy boʻlishi mumkin emas");
            }
            offer.setAvailableQuantity(request.availableQuantity());
        }

        if (request.minOrderQuantity() != null) {
            if (request.minOrderQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessConflictException("Minimal buyurtma miqdori noldan katta boʻlishi kerak");
            }
            offer.setMinOrderQuantity(request.minOrderQuantity());
        }

        if (request.stepQuantity() != null) {
            if (request.stepQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessConflictException("Qadam miqdori noldan katta boʻlishi kerak");
            }
            offer.setStepQuantity(request.stepQuantity());
        }

        if (request.currency() != null && !request.currency().isBlank()) {
            offer.setCurrency(request.currency().trim().toUpperCase());
        }

        if (request.region() != null) {
            offer.setRegion(request.region().trim());
        }

        if (request.district() != null) {
            offer.setDistrict(request.district().trim());
        }

        if (request.notes() != null) {
            offer.setNotes(request.notes().trim());
        }

        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toUpperCase();
            if (!List.of("DRAFT", "ACTIVE", "PAUSED", "OUT_OF_STOCK", "ARCHIVED").contains(status)) {
                throw new BusinessConflictException("Notoʻgʻri holat koʻrsatildi: " + status);
            }
            offer.setStatus(status);
        }

        SellerOfferEntity saved = sellerOfferRepository.save(offer);
        log.info("Seller offer updated: id={}, seller={}", saved.getId(), sellerUserId);

        return SellerOfferDetailDto.fromEntity(
                saved,
                product.getNameUz(),
                product.getNameRu(),
                product.getBrandName()
        );
    }

    /**
     * Deactivates/archives an offer when it should no longer be purchasable.
     */
    public SellerOfferDetailDto deactivateOffer(UUID offerId, UUID sellerUserId) {
        SellerOfferEntity offer = sellerOfferRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Taklif topilmadi: " + offerId));

        if (!offer.getSellerUserId().equals(sellerUserId)) {
            throw new AccessDeniedException("Faqat taklif egasi ushbu taklifni bekor qila oladi");
        }

        offer.setStatus("ARCHIVED");
        SellerOfferEntity saved = sellerOfferRepository.save(offer);
        log.info("Seller offer deactivated (ARCHIVED): id={}, seller={}", saved.getId(), sellerUserId);

        CatalogProductEntity product = catalogProductRepository.findById(saved.getCatalogProductId()).orElse(null);
        String nameUz = product != null ? product.getNameUz() : null;
        String nameRu = product != null ? product.getNameRu() : null;
        String brand = product != null ? product.getBrandName() : null;

        return SellerOfferDetailDto.fromEntity(saved, nameUz, nameRu, brand);
    }
}
