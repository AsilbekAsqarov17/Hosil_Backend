package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;

import java.util.List;
import java.util.UUID;

public interface CatalogProductRepository extends JpaRepository<CatalogProductEntity, UUID>, JpaSpecificationExecutor<CatalogProductEntity> {

    List<CatalogProductEntity> findByCategoryIdAndIsActiveTrue(UUID categoryId);
}
