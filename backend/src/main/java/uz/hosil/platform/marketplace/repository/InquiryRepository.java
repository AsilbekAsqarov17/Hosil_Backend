package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.hosil.platform.marketplace.domain.InquiryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<InquiryEntity, UUID>, JpaSpecificationExecutor<InquiryEntity> {

    Optional<InquiryEntity> findByBuyerUserIdAndIdempotencyKey(UUID buyerUserId, String idempotencyKey);

    List<InquiryEntity> findByHarvestListingId(UUID harvestListingId);
}
