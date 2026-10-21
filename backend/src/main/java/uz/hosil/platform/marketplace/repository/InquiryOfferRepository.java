package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryOfferRepository extends JpaRepository<InquiryOfferEntity, UUID> {

    List<InquiryOfferEntity> findByInquiryIdOrderByOfferNumberAsc(UUID inquiryId);

    Optional<InquiryOfferEntity> findByInquiryIdAndProposerUserIdAndIdempotencyKey(UUID inquiryId, UUID proposerUserId, String idempotencyKey);
}
