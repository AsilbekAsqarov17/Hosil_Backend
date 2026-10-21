package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrderId(UUID orderId);
}
