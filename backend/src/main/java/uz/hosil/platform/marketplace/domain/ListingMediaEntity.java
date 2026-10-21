package uz.hosil.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Links approved public media photos to a harvest listing.
 */
@Entity
@Table(name = "listing_media")
public class ListingMediaEntity {

    @Id
    private UUID id;

    @Column(name = "harvest_listing_id", nullable = false)
    private UUID harvestListingId;

    @Column(name = "media_file_id", nullable = false)
    private UUID mediaFileId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ListingMediaEntity() {}

    public ListingMediaEntity(UUID id, UUID harvestListingId, UUID mediaFileId, Integer displayOrder) {
        this.id = id != null ? id : UUID.randomUUID();
        this.harvestListingId = harvestListingId;
        this.mediaFileId = mediaFileId;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHarvestListingId() { return harvestListingId; }
    public void setHarvestListingId(UUID harvestListingId) { this.harvestListingId = harvestListingId; }

    public UUID getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(UUID mediaFileId) { this.mediaFileId = mediaFileId; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
