package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.CategoryAllowedUnitEntity;

import java.util.List;
import java.util.UUID;

public interface CategoryAllowedUnitRepository extends JpaRepository<CategoryAllowedUnitEntity, CategoryAllowedUnitEntity.CategoryAllowedUnitId> {

    boolean existsByIdCategoryIdAndIdUnitCode(UUID categoryId, String unitCode);

    List<CategoryAllowedUnitEntity> findByIdCategoryId(UUID categoryId);
}
