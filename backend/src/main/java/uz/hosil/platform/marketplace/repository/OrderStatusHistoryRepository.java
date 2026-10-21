package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
