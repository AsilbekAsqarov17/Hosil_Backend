package uz.hosil.platform.marketplace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.marketplace.domain.OrderEntity;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByBuyerUserIdAndIdempotencyKey(UUID buyerUserId, String idempotencyKey);

    Optional<OrderEntity> findByInquiryId(UUID inquiryId);

    Optional<OrderEntity> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE (o.buyerUserId = :userId OR o.sellerUserId = :userId) AND o.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);
}
