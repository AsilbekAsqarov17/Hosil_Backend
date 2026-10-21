package uz.hosil.platform.marketplace.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.ListingMediaEntity;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.ListingMediaRepository;
import uz.hosil.platform.marketplace.web.HarvestListingDto;
import uz.hosil.platform.marketplace.web.PageEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * High-performance, read-oriented discovery service for wholesale harvest listings on Hosil Bozor.
 * Enforces status filtering, input validations, deterministic tie-breaking, and O(1) media batch fetching.
 */
@Service
@Transactional(readOnly = true)
public class HarvestDiscoveryService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final HarvestListingRepository listingRepository;
    private final ListingMediaRepository mediaRepository;

    public HarvestDiscoveryService(
            HarvestListingRepository listingRepository,
            ListingMediaRepository mediaRepository
    ) {
        this.listingRepository = listingRepository;
        this.mediaRepository = mediaRepository;
    }

    public PageEnvelope<HarvestListingDto> searchPublishedListings(
            HarvestListingFilter filter,
            Pageable pageable
    ) {
        if (filter != null) {
            filter.validate();
        }

        Specification<HarvestListingEntity> spec = createSpecification(filter);
        Pageable sortedPageable = applyDeterministicSorting(pageable);

        Page<HarvestListingEntity> page = listingRepository.findAll(spec, sortedPageable);

        List<UUID> listingIds = page.getContent().stream()
                .map(HarvestListingEntity::getId)
                .toList();

        Map<UUID, List<UUID>> mediaMap = listingIds.isEmpty()
                ? Map.of()
                : mediaRepository.findByHarvestListingIdInOrderByDisplayOrderAsc(listingIds).stream()
                        .collect(Collectors.groupingBy(
                                ListingMediaEntity::getHarvestListingId,
                                Collectors.mapping(ListingMediaEntity::getMediaFileId, Collectors.toList())
                        ));

        List<HarvestListingDto> content = page.getContent().stream()
                .map(entity -> HarvestListingDto.fromEntity(entity, mediaMap.getOrDefault(entity.getId(), List.of())))
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

    public HarvestListingDto getPublishedListingDetail(UUID listingId) {
        HarvestListingEntity entity = listingRepository.findByIdAndStatus(listingId, STATUS_PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Eʼlon topilmadi yoki faol emas: " + listingId));

        List<UUID> mediaIds = mediaRepository.findByHarvestListingIdOrderByDisplayOrderAsc(listingId).stream()
                .map(ListingMediaEntity::getMediaFileId)
                .toList();

        return HarvestListingDto.fromEntity(entity, mediaIds);
    }

    private Specification<HarvestListingEntity> createSpecification(HarvestListingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Rule: strictly only PUBLISHED listings are discovered
            predicates.add(cb.equal(root.get("status"), STATUS_PUBLISHED));

            if (filter != null) {
                if (filter.cropType() != null && !filter.cropType().isBlank()) {
                    predicates.add(cb.like(
                            cb.lower(root.get("cropType")),
                            "%" + filter.cropType().toLowerCase().trim() + "%"
                    ));
                }

                if (filter.region() != null && !filter.region().isBlank()) {
                    predicates.add(cb.like(
                            cb.lower(root.get("region")),
                            "%" + filter.region().toLowerCase().trim() + "%"
                    ));
                }

                if (filter.district() != null && !filter.district().isBlank()) {
                    predicates.add(cb.like(
                            cb.lower(root.get("district")),
                            "%" + filter.district().toLowerCase().trim() + "%"
                    ));
                }

                if (filter.qualityGrade() != null && !filter.qualityGrade().isBlank()) {
                    predicates.add(cb.equal(
                            root.get("qualityGrade"),
                            filter.qualityGrade().toUpperCase().trim()
                    ));
                }

                if (filter.minPrice() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), filter.minPrice()));
                }

                if (filter.maxPrice() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), filter.maxPrice()));
                }

                if (filter.isRfqEnabled() != null) {
                    predicates.add(cb.equal(root.get("isRfqEnabled"), filter.isRfqEnabled()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Pageable applyDeterministicSorting(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        } else {
            boolean hasIdSort = sort.stream().anyMatch(order -> order.getProperty().equalsIgnoreCase("id"));
            if (!hasIdSort) {
                sort = sort.and(Sort.by(Sort.Order.desc("id")));
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
