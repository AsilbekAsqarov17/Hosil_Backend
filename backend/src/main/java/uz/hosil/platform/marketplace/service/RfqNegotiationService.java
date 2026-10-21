package uz.hosil.platform.marketplace.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.web.CreateInquiryRequest;
import uz.hosil.platform.marketplace.web.InquiryDetailDto;
import uz.hosil.platform.marketplace.web.InquirySummaryDto;
import uz.hosil.platform.marketplace.web.PageEnvelope;
import uz.hosil.platform.marketplace.web.SubmitOfferRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service managing buyer Request-for-Quote (RFQ) inquiries, immutable offer sequences,
 * turn-taking validation, and binding agreed commercial terms (DEC-006).
 */
@Service
@Transactional
public class RfqNegotiationService {

    private final InquiryRepository inquiryRepository;
    private final InquiryOfferRepository inquiryOfferRepository;
    private final HarvestListingRepository listingRepository;

    public RfqNegotiationService(
            InquiryRepository inquiryRepository,
            InquiryOfferRepository inquiryOfferRepository,
            HarvestListingRepository listingRepository
    ) {
        this.inquiryRepository = inquiryRepository;
        this.inquiryOfferRepository = inquiryOfferRepository;
        this.listingRepository = listingRepository;
    }

    public record InquiryOperationResult(InquiryDetailDto dto, boolean created) {}

    /**
     * Buyer initiates an RFQ negotiation on an active, published harvest listing.
     */
    public InquiryOperationResult submitInquiry(
            UUID listingId,
            CreateInquiryRequest request,
            String idempotencyKey,
            UUID buyerUserId
    ) {
        // 1. Idempotent hit check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<InquiryEntity> existing = inquiryRepository.findByBuyerUserIdAndIdempotencyKey(buyerUserId, idempotencyKey);
            if (existing.isPresent()) {
                InquiryDetailDto dto = loadInquiryDetail(existing.get().getId());
                return new InquiryOperationResult(dto, false);
            }
        }

        // 2. Fetch and validate harvest listing
        HarvestListingEntity listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Eʼlon topilmadi: " + listingId));

        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new BusinessConflictException("Faqat faol va eʼlon qilingan mahsulotlarga soʻrov yuborish mumkin. Joriy holat: " + listing.getStatus());
        }

        if (!Boolean.TRUE.equals(listing.getIsRfqEnabled())) {
            throw new BusinessConflictException("Ushbu eʼlonda RFQ (narx soʻrash) rejimi yoqilmagan. Toʻgʻridan-toʻgʻri xarid oqimidan foydalaning.");
        }

        if (listing.getSellerUserId().equals(buyerUserId)) {
            throw new BusinessConflictException("Sotuvchi oʻzining mahsulot eʼloniga xarid soʻrovi yubora olmaydi.");
        }

        if (!listing.getUnitCode().equalsIgnoreCase(request.unitCode().trim())) {
            throw new BusinessConflictException("Oʻlchov birligi mos kelmadi: eʼlon " + listing.getUnitCode() + ", soʻrov " + request.unitCode());
        }

        BigDecimal qty = request.quantity();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Miqdor noldan katta boʻlishi kerak.");
        }

        if (qty.compareTo(listing.getMinOrderQuantity()) < 0) {
            throw new BusinessConflictException("Soʻralgan miqdor (" + qty + ") minimal buyurtma miqdoridan (" + listing.getMinOrderQuantity() + ") kam boʻlishi mumkin emas.");
        }

        if (listing.getStepQuantity() != null && listing.getStepQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = qty.subtract(listing.getMinOrderQuantity());
            BigDecimal[] divRem = diff.divideAndRemainder(listing.getStepQuantity());
            if (divRem[1].compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessConflictException("Miqdor (" + qty + ") belgilangan qadam (" + listing.getStepQuantity() + ") boʻyicha boʻlishi kerak.");
            }
        }

        if (qty.compareTo(listing.getAvailableQuantity()) > 0) {
            throw new BusinessConflictException("Soʻralgan miqdor (" + qty + ") mavjud qoldiqdan (" + listing.getAvailableQuantity() + ") koʻp boʻlishi mumkin emas.");
        }

        if (request.unitPrice() != null && request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Taklif etilgan narx noldan katta boʻlishi kerak.");
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Taklifning amal qilish muddati oʻtib ketgan sana boʻlishi mumkin emas.");
        }

        // 3. Persist inquiry entity
        InquiryEntity inquiry = new InquiryEntity(
                UUID.randomUUID(),
                listing.getId(),
                buyerUserId,
                listing.getSellerUserId(),
                listing.getCropType(),
                listing.getUnitCode(),
                idempotencyKey
        );
        inquiry = inquiryRepository.save(inquiry);

        // 4. Persist initial offer (#1)
        InquiryOfferEntity initialOffer = new InquiryOfferEntity(
                UUID.randomUUID(),
                inquiry.getId(),
                1,
                buyerUserId,
                "BUYER",
                qty,
                listing.getUnitCode(),
                request.unitPrice(),
                request.currency() != null ? request.currency() : listing.getCurrency(),
                request.deliveryTerms(),
                request.notes(),
                request.expiresAt(),
                idempotencyKey
        );
        initialOffer = inquiryOfferRepository.save(initialOffer);

        // 5. Link current offer to inquiry
        inquiry.setCurrentOfferId(initialOffer.getId());
        inquiry = inquiryRepository.save(inquiry);

        InquiryDetailDto dto = InquiryDetailDto.of(inquiry, initialOffer, null, List.of(initialOffer));
        return new InquiryOperationResult(dto, true);
    }

    /**
     * Submits a counteroffer in sequence, transferring turn to the counterparty.
     */
    public InquiryOperationResult submitCounterOffer(
            UUID inquiryId,
            SubmitOfferRequest request,
            String idempotencyKey,
            UUID callerUserId
    ) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        boolean isBuyer = inquiry.getBuyerUserId().equals(callerUserId);
        boolean isSeller = inquiry.getSellerUserId().equals(callerUserId);

        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Siz ushbu soʻrov muzokarasida ishtirokchi emassiz.");
        }

        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<InquiryOfferEntity> existing = inquiryOfferRepository.findByInquiryIdAndProposerUserIdAndIdempotencyKey(
                    inquiryId, callerUserId, idempotencyKey
            );
            if (existing.isPresent()) {
                InquiryDetailDto dto = loadInquiryDetail(inquiryId);
                return new InquiryOperationResult(dto, false);
            }
        }

        if (!"OPEN".equals(inquiry.getStatus())) {
            throw new BusinessConflictException("Yopilgan yoki kelishilgan soʻrovga taklif yuborib boʻlmaydi. Holat: " + inquiry.getStatus());
        }

        InquiryOfferEntity currentOffer = inquiryOfferRepository.findById(inquiry.getCurrentOfferId())
                .orElseThrow(() -> new IllegalStateException("Joriy taklif topilmadi: " + inquiry.getCurrentOfferId()));

        String expectedRole = "BUYER".equals(currentOffer.getProposerRole()) ? "SELLER" : "BUYER";
        String callerRole = isBuyer ? "BUYER" : "SELLER";

        if (!expectedRole.equals(callerRole)) {
            throw new BusinessConflictException("Navbat sizda emas. Hozirda javob kutayotgan tomon: " + expectedRole);
        }

        // Check if current offer expired
        if (currentOffer.getExpiresAt() != null && currentOffer.getExpiresAt().isBefore(Instant.now())) {
            currentOffer.setStatus("EXPIRED");
            inquiryOfferRepository.save(currentOffer);
            throw new BusinessConflictException("OFFER_EXPIRED: Amaldagi taklif muddati oʻtib ketgan (" + currentOffer.getExpiresAt() + ").");
        }

        HarvestListingEntity listing = listingRepository.findById(inquiry.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Eʼlon topilmadi: " + inquiry.getHarvestListingId()));

        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new BusinessConflictException("Mahsulot eʼloni faol emas: " + listing.getStatus());
        }

        BigDecimal qty = request.quantity();
        if (qty.compareTo(listing.getMinOrderQuantity()) < 0) {
            throw new BusinessConflictException("Miqdor (" + qty + ") minimal buyurtma miqdoridan (" + listing.getMinOrderQuantity() + ") kam boʻlishi mumkin emas.");
        }

        if (qty.compareTo(listing.getAvailableQuantity()) > 0) {
            throw new BusinessConflictException("Miqdor (" + qty + ") mavjud qoldiqdan (" + listing.getAvailableQuantity() + ") koʻp boʻlishi mumkin emas.");
        }

        if (request.unitPrice() == null || request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Qarshi taklif narxi noldan katta boʻlishi shart.");
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Taklifning amal qilish muddati oʻtib ketgan sana boʻlishi mumkin emas.");
        }

        // 1. Mark previous active offer as SUPERSEDED
        currentOffer.setStatus("SUPERSEDED");
        inquiryOfferRepository.save(currentOffer);

        // 2. Insert new offer in sequence
        int nextOfferNumber = currentOffer.getOfferNumber() + 1;
        InquiryOfferEntity newOffer = new InquiryOfferEntity(
                UUID.randomUUID(),
                inquiry.getId(),
                nextOfferNumber,
                callerUserId,
                callerRole,
                qty,
                listing.getUnitCode(),
                request.unitPrice(),
                request.currency() != null ? request.currency() : listing.getCurrency(),
                request.deliveryTerms(),
                request.notes(),
                request.expiresAt(),
                idempotencyKey
        );
        newOffer = inquiryOfferRepository.save(newOffer);

        // 3. Update current offer pointer on inquiry
        inquiry.setCurrentOfferId(newOffer.getId());
        inquiryRepository.save(inquiry);

        InquiryDetailDto dto = loadInquiryDetail(inquiryId);
        return new InquiryOperationResult(dto, true);
    }

    /**
     * Recipient of the active proposal accepts it, binding agreed commercial terms (DEC-006).
     */
    public InquiryDetailDto acceptOffer(UUID inquiryId, UUID callerUserId) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        boolean isBuyer = inquiry.getBuyerUserId().equals(callerUserId);
        boolean isSeller = inquiry.getSellerUserId().equals(callerUserId);

        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Siz ushbu soʻrov muzokarasida ishtirokchi emassiz.");
        }

        if (!"OPEN".equals(inquiry.getStatus())) {
            throw new BusinessConflictException("Muzokarani qabul qilib boʻlmaydi. Joriy holat: " + inquiry.getStatus());
        }

        InquiryOfferEntity currentOffer = inquiryOfferRepository.findById(inquiry.getCurrentOfferId())
                .orElseThrow(() -> new IllegalStateException("Amaldagi taklif topilmadi: " + inquiry.getCurrentOfferId()));

        if (!"PROPOSED".equals(currentOffer.getStatus())) {
            throw new BusinessConflictException("Qabul qilinayotgan taklif faol emas. Taklif holati: " + currentOffer.getStatus());
        }

        if (currentOffer.getProposerUserId().equals(callerUserId)) {
            throw new BusinessConflictException("Foydalanuvchi oʻzi bergan taklifni qabul qila olmaydi. Faqat qarshi tomon qabul qilishi mumkin.");
        }

        if (currentOffer.getUnitPrice() == null || currentOffer.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Narx belgilanmagan taklifni qabul qilib boʻlmaydi. Avval aniq narx bilan qarshi taklif berilishi lozim.");
        }

        if (currentOffer.getExpiresAt() != null && currentOffer.getExpiresAt().isBefore(Instant.now())) {
            currentOffer.setStatus("EXPIRED");
            inquiryOfferRepository.save(currentOffer);
            throw new BusinessConflictException("OFFER_EXPIRED: Taklifning amal qilish muddati tugagan (" + currentOffer.getExpiresAt() + ").");
        }

        HarvestListingEntity listing = listingRepository.findById(inquiry.getHarvestListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Eʼlon topilmadi: " + inquiry.getHarvestListingId()));

        if (currentOffer.getQuantity().compareTo(listing.getAvailableQuantity()) > 0) {
            throw new BusinessConflictException("Kelishilayotgan miqdor (" + currentOffer.getQuantity() + ") sotuvchining amaldagi qoldigʻidan (" + listing.getAvailableQuantity() + ") koʻp.");
        }

        // 1. Mark offer as ACCEPTED
        currentOffer.setStatus("ACCEPTED");
        inquiryOfferRepository.save(currentOffer);

        // 2. Bind agreed terms on inquiry
        inquiry.setStatus("AGREED");
        inquiry.setAgreedOfferId(currentOffer.getId());
        inquiry.setAgreedAt(Instant.now());
        inquiry.setAgreedQuantity(currentOffer.getQuantity());
        inquiry.setAgreedUnitPrice(currentOffer.getUnitPrice());
        inquiry.setAgreedCurrency(currentOffer.getCurrency());
        inquiry.setAgreedDeliveryTerms(currentOffer.getDeliveryTerms());
        inquiryRepository.save(inquiry);

        return loadInquiryDetail(inquiryId);
    }

    /**
     * Terminates an open inquiry with rejection.
     */
    public InquiryDetailDto rejectInquiry(UUID inquiryId, String reason, UUID callerUserId) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        if (!inquiry.getBuyerUserId().equals(callerUserId) && !inquiry.getSellerUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Siz ushbu soʻrov muzokarasida ishtirokchi emassiz.");
        }

        if (!"OPEN".equals(inquiry.getStatus())) {
            throw new BusinessConflictException("Faqat ochiq holatdagi soʻrovni rad etish mumkin. Joriy holat: " + inquiry.getStatus());
        }

        if (inquiry.getCurrentOfferId() != null) {
            inquiryOfferRepository.findById(inquiry.getCurrentOfferId()).ifPresent(offer -> {
                offer.setStatus("REJECTED");
                inquiryOfferRepository.save(offer);
            });
        }

        inquiry.setStatus("REJECTED");
        inquiry.setRejectionReason(reason);
        inquiryRepository.save(inquiry);

        return loadInquiryDetail(inquiryId);
    }

    /**
     * Buyer withdraws their open inquiry.
     */
    public InquiryDetailDto withdrawInquiry(UUID inquiryId, String reason, UUID callerUserId) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        if (!inquiry.getBuyerUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Faqat soʻrovni yuborgan xaridor uni qaytarib olishi mumkin.");
        }

        if (!"OPEN".equals(inquiry.getStatus())) {
            throw new BusinessConflictException("Faqat ochiq holatdagi soʻrovni qaytarib olish mumkin. Joriy holat: " + inquiry.getStatus());
        }

        if (inquiry.getCurrentOfferId() != null) {
            inquiryOfferRepository.findById(inquiry.getCurrentOfferId()).ifPresent(offer -> {
                offer.setStatus("SUPERSEDED");
                inquiryOfferRepository.save(offer);
            });
        }

        inquiry.setStatus("WITHDRAWN");
        inquiry.setRejectionReason(reason);
        inquiryRepository.save(inquiry);

        return loadInquiryDetail(inquiryId);
    }

    /**
     * Retrieves full inquiry details including complete offer history.
     */
    @Transactional(readOnly = true)
    public InquiryDetailDto getInquiryDetail(UUID inquiryId, UUID callerUserId) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        if (!inquiry.getBuyerUserId().equals(callerUserId) && !inquiry.getSellerUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Siz ushbu soʻrov maʼlumotlarini koʻrish huquqiga ega emassiz.");
        }

        return loadInquiryDetail(inquiryId);
    }

    /**
     * Lists paginated inquiries where the caller is either buyer or seller.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<InquirySummaryDto> listMyInquiries(
            String role,
            String status,
            UUID listingId,
            Pageable pageable,
            UUID callerUserId
    ) {
        Specification<InquiryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // User role filter
            if (role == null || role.isBlank()) {
                predicates.add(cb.or(
                        cb.equal(root.get("buyerUserId"), callerUserId),
                        cb.equal(root.get("sellerUserId"), callerUserId)
                ));
            } else if ("BUYER".equalsIgnoreCase(role.trim())) {
                predicates.add(cb.equal(root.get("buyerUserId"), callerUserId));
            } else if ("SELLER".equalsIgnoreCase(role.trim())) {
                predicates.add(cb.equal(root.get("sellerUserId"), callerUserId));
            } else {
                throw new IllegalArgumentException("Notoʻgʻri rol parametri: " + role + ". Faqat BUYER yoki SELLER ruxsat etiladi.");
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.toUpperCase().trim()));
            }

            if (listingId != null) {
                predicates.add(cb.equal(root.get("harvestListingId"), listingId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Deterministic sorting with id tie-breaker
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        } else {
            boolean hasId = sort.stream().anyMatch(o -> o.getProperty().equalsIgnoreCase("id"));
            if (!hasId) {
                sort = sort.and(Sort.by(Sort.Order.desc("id")));
            }
        }
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<InquiryEntity> page = inquiryRepository.findAll(spec, sortedPageable);

        List<UUID> currentOfferIds = page.getContent().stream()
                .map(InquiryEntity::getCurrentOfferId)
                .filter(id -> id != null)
                .toList();

        Map<UUID, InquiryOfferEntity> offerMap = currentOfferIds.isEmpty()
                ? Map.of()
                : inquiryOfferRepository.findAllById(currentOfferIds).stream()
                        .collect(Collectors.toMap(InquiryOfferEntity::getId, Function.identity()));

        List<InquirySummaryDto> content = page.getContent().stream()
                .map(inquiry -> {
                    InquiryOfferEntity currentOffer = inquiry.getCurrentOfferId() != null
                            ? offerMap.get(inquiry.getCurrentOfferId())
                            : null;
                    return InquirySummaryDto.fromEntity(inquiry, currentOffer);
                })
                .toList();

        return new PageEnvelope<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    private InquiryDetailDto loadInquiryDetail(UUID inquiryId) {
        InquiryEntity inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Soʻrov topilmadi: " + inquiryId));

        List<InquiryOfferEntity> offers = inquiryOfferRepository.findByInquiryIdOrderByOfferNumberAsc(inquiryId);

        InquiryOfferEntity currentOffer = inquiry.getCurrentOfferId() != null
                ? inquiryOfferRepository.findById(inquiry.getCurrentOfferId()).orElse(null)
                : null;

        InquiryOfferEntity agreedOffer = inquiry.getAgreedOfferId() != null
                ? inquiryOfferRepository.findById(inquiry.getAgreedOfferId()).orElse(null)
                : null;

        return InquiryDetailDto.of(inquiry, currentOffer, agreedOffer, offers);
    }
}
