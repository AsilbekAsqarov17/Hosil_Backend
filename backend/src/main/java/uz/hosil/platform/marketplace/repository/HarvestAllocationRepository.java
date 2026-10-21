package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.HarvestAllocationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HarvestAllocationRepository extends JpaRepository<HarvestAllocationEntity, UUID> {

    List<HarvestAllocationEntity> findByHarvestBatchId(UUID harvestBatchId);

    Optional<HarvestAllocationEntity> findByHarvestListingId(UUID harvestListingId);
}
