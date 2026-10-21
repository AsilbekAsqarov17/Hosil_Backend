package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerOfferRepository extends JpaRepository<SellerOfferEntity, UUID>, JpaSpecificationExecutor<SellerOfferEntity> {

    List<SellerOfferEntity> findByCatalogProductIdAndStatusAndAvailableQuantityGreaterThan(
            UUID catalogProductId, String status, BigDecimal quantity
    );

    List<SellerOfferEntity> findBySellerUserId(UUID sellerUserId);

    Optional<SellerOfferEntity> findByIdAndSellerUserId(UUID id, UUID sellerUserId);

    @Query("SELECT COUNT(o) FROM SellerOfferEntity o WHERE o.catalogProductId = :productId AND o.status = 'ACTIVE' AND o.availableQuantity > 0")
    long countActiveOffersByProductId(@Param("productId") UUID productId);

    @Query("SELECT MIN(o.unitPrice) FROM SellerOfferEntity o WHERE o.catalogProductId = :productId AND o.status = 'ACTIVE' AND o.availableQuantity > 0")
    BigDecimal findMinPriceByProductId(@Param("productId") UUID productId);

    @Query("SELECT MAX(o.unitPrice) FROM SellerOfferEntity o WHERE o.catalogProductId = :productId AND o.status = 'ACTIVE' AND o.availableQuantity > 0")
    BigDecimal findMaxPriceByProductId(@Param("productId") UUID productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SellerOfferEntity o WHERE o.id = :id")
    Optional<SellerOfferEntity> findByIdForUpdate(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SellerOfferEntity o WHERE o.id IN :ids ORDER BY o.id ASC")
    List<SellerOfferEntity> findAllByIdInForUpdate(@Param("ids") List<UUID> ids);
}
