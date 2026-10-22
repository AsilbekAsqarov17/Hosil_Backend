package uz.hosil.platform.marketplace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.marketplace.domain.PaymentEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByPaymentIntentReference(String paymentIntentReference);

    Optional<PaymentEntity> findByBuyerUserIdAndIdempotencyKey(UUID buyerUserId, String idempotencyKey);

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentEntity> findFirstByOrderIdAndStatus(UUID orderId, String status);

    List<PaymentEntity> findByStatusAndCreatedAtBefore(String status, Instant threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    Optional<PaymentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.paymentIntentReference = :ref")
    Optional<PaymentEntity> findByPaymentIntentReferenceForUpdate(@Param("ref") String ref);
}
