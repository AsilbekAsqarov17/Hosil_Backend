package uz.hosil.platform.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hosil.platform.marketplace.domain.ListingMediaEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ListingMediaRepository extends JpaRepository<ListingMediaEntity, UUID> {

    List<ListingMediaEntity> findByHarvestListingIdOrderByDisplayOrderAsc(UUID harvestListingId);

    List<ListingMediaEntity> findByHarvestListingIdInOrderByDisplayOrderAsc(Collection<UUID> harvestListingIds);
}
