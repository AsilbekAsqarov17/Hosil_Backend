package uz.hosil.platform.farm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.hosil.platform.farm.domain.FarmEntity;

import java.util.UUID;

@Repository
public interface FarmRepository extends JpaRepository<FarmEntity, UUID> {

    @Query("SELECT f FROM FarmEntity f WHERE f.id IN (" +
           "  SELECT m.farmId FROM FarmMembershipEntity m WHERE m.userId = :userId AND m.status = 'ACTIVE'" +
           ") ORDER BY f.createdAt DESC")
    Page<FarmEntity> findAccessibleFarms(@Param("userId") UUID userId, Pageable pageable);
}
