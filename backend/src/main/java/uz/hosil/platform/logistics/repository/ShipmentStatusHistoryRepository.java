package uz.hosil.platform.logistics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.logistics.domain.ShipmentStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistoryEntity, UUID> {

    List<ShipmentStatusHistoryEntity> findByShipmentRequestIdOrderByCreatedAtAsc(UUID shipmentRequestId);
}
