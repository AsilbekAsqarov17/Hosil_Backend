package uz.hosil.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.identity.domain.TelegramProcessedUpdateEntity;

@Repository
public interface TelegramProcessedUpdateRepository extends JpaRepository<TelegramProcessedUpdateEntity, Long> {
}
