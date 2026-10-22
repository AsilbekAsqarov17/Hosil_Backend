package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.PaymentEventEntity;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {

    boolean existsByPaymentIdAndExternalEventId(UUID paymentId, String externalEventId);

    boolean existsByExternalEventId(String externalEventId);

    List<PaymentEventEntity> findByPaymentIdOrderByRecordedAtAsc(UUID paymentId);
}
