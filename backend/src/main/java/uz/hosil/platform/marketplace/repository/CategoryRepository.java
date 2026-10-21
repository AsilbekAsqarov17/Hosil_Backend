package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.CategoryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    Optional<CategoryEntity> findByCode(String code);

    List<CategoryEntity> findByIsActiveTrueOrderBySortOrderAsc();

    List<CategoryEntity> findByCategoryTypeAndIsActiveTrueOrderBySortOrderAsc(String categoryType);
}
