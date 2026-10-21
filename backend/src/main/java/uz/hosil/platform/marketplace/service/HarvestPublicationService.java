package uz.hosil.platform.marketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.farm.AllocatedHarvestBatchInfo;
import uz.hosil.platform.farm.FarmHarvestBridge;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestAllocationEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.ListingMediaEntity;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.ListingMediaRepository;
import uz.hosil.platform.marketplace.web.CreateHarvestListingRequest;
import uz.hosil.platform.marketplace.web.HarvestListingDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HarvestPublicationService {

    private static final Logger log = LoggerFactory.getLogger(HarvestPublicationService.class);

    private final FarmHarvestBridge farmHarvestBridge;
    private final HarvestListingRepository listingRepository;
    private final HarvestAllocationRepository allocationRepository;
    private final ListingMediaRepository mediaRepository;
    private final CategoryRepository categoryRepository;

    public HarvestPublicationService(
            FarmHarvestBridge farmHarvestBridge,
            HarvestListingRepository listingRepository,
            HarvestAllocationRepository allocationRepository,
            ListingMediaRepository mediaRepository,
            CategoryRepository categoryRepository
    ) {
        this.farmHarvestBridge = farmHarvestBridge;
        this.listingRepository = listingRepository;
        this.allocationRepository = allocationRepository;
        this.mediaRepository = mediaRepository;
        this.categoryRepository = categoryRepository;
    }

    public record PublicationResult(HarvestListingDto dto, boolean created) {}

    /**
     * Atomically publishes a harvest listing to Hosil Bozor within a single transactional boundary.
     * Enforces pessimistic locking on the backing harvest batch, strict quantity conservation,
     * allocation ledger logging, and commercial data privacy.
     */
    @Transactional
    public PublicationResult publishHarvestListing(
            CreateHarvestListingRequest req,
            String idempotencyKey,
            UUID callerUserId
    ) {
        // 1. Idempotency replay check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<HarvestListingEntity> existing = listingRepository.findBySellerUserIdAndIdempotencyKey(
                    callerUserId, idempotencyKey
            );
            if (existing.isPresent()) {
                HarvestListingEntity entity = existing.get();
                List<UUID> mediaIds = mediaRepository.findByHarvestListingIdOrderByDisplayOrderAsc(entity.getId())
                        .stream().map(ListingMediaEntity::getMediaFileId).toList();
                log.info("Idempotent replay detected for harvest listing publication: key={}, id={}",
                        idempotencyKey, entity.getId());
                return new PublicationResult(HarvestListingDto.fromEntity(entity, mediaIds), false);
            }
        }

        // 2. Validate listing parameters
        BigDecimal allocQty;
        BigDecimal moq;
        BigDecimal step;
        try {
            allocQty = new BigDecimal(req.allocatedQuantity());
            moq = new BigDecimal(req.minOrderQuantity());
            step = new BigDecimal(req.stepQuantity());
        } catch (NumberFormatException e) {
            throw new BusinessConflictException("Invalid numerical quantity format in listing request");
        }

        if (allocQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Allocated quantity must be greater than zero");
        }
        if (moq.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Minimum order quantity must be greater than zero");
        }
        if (moq.compareTo(allocQty) > 0) {
            throw new BusinessConflictException("Minimum order quantity (" + moq + ") cannot exceed allocated quantity (" + allocQty + ")");
        }
        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Order step quantity must be greater than zero");
        }
        if (step.compareTo(moq) > 0) {
            throw new BusinessConflictException("Order step quantity (" + step + ") cannot exceed minimum order quantity (" + moq + ")");
        }

        boolean isRfq = Boolean.TRUE.equals(req.isRfqEnabled());
        BigDecimal price = null;
        if (!isRfq) {
            if (req.unitPrice() == null || req.unitPrice().isBlank()) {
                throw new BusinessConflictException("unitPrice is required when isRfqEnabled is false");
            }
            try {
                price = new BigDecimal(req.unitPrice());
            } catch (NumberFormatException e) {
                throw new BusinessConflictException("Invalid unitPrice format: " + req.unitPrice());
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessConflictException("unitPrice must be greater than zero");
            }
        }

        // 3. Atomically lock harvest batch with SELECT ... FOR UPDATE and allocate stock
        AllocatedHarvestBatchInfo batchInfo = farmHarvestBridge.lockAndAllocateHarvestBatch(
                req.harvestBatchId(),
                allocQty,
                req.unitCode(),
                callerUserId
        );

        // 4. Resolve Category (HARVEST root category)
        CategoryEntity category = categoryRepository.findByCode("HARVEST")
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(
                        UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 1
                )));

        UUID listingId = UUID.randomUUID();
        String region = (req.region() != null && !req.region().isBlank()) ? req.region() : batchInfo.region();
        String district = (req.district() != null && !req.district().isBlank()) ? req.district() : batchInfo.district();
        String qualityGrade = req.qualityGrade() != null ? req.qualityGrade() : batchInfo.qualityGrade();

        // 5. Create HarvestListingEntity (enforces single-row quantity balance)
        HarvestListingEntity listing = new HarvestListingEntity(
                listingId,
                callerUserId,
                batchInfo.batchId(),
                category.getId(),
                req.cropType(),
                null, // description
                req.cropType(),
                qualityGrade,
                price,
                req.currency() != null ? req.currency() : "UZS",
                isRfq ? "RFQ" : "FIXED",
                isRfq,
                allocQty,
                allocQty, // availableQuantity initialized strictly to allocQty
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                batchInfo.unitCode(),
                moq,
                step,
                region,
                district,
                "PUBLISHED",
                idempotencyKey,
                Instant.now()
        );
        listing = listingRepository.save(listing);

        // 6. Create HarvestAllocationEntity (allocation ledger DEC-008)
        HarvestAllocationEntity allocation = new HarvestAllocationEntity(
                UUID.randomUUID(),
                batchInfo.batchId(),
                listing.getId(),
                allocQty,
                batchInfo.unitCode(),
                "ACTIVE",
                Instant.now()
        );
        allocationRepository.save(allocation);

        // 7. Persist media photos if provided
        List<UUID> attachedMedia = new ArrayList<>();
        if (req.mediaFileIds() != null && !req.mediaFileIds().isEmpty()) {
            int order = 0;
            for (UUID mediaId : req.mediaFileIds()) {
                ListingMediaEntity mediaEntity = new ListingMediaEntity(
                        UUID.randomUUID(),
                        listing.getId(),
                        mediaId,
                        order++
                );
                mediaRepository.save(mediaEntity);
                attachedMedia.add(mediaId);
            }
        }

        log.info("Harvest listing {} successfully published by seller {}. Allocated: {} {}",
                listing.getId(), callerUserId, allocQty, batchInfo.unitCode());

        return new PublicationResult(HarvestListingDto.fromEntity(listing, attachedMedia), true);
    }
}
