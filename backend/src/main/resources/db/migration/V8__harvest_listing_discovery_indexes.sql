-- =============================================================================
-- Migration: V8__harvest_listing_discovery_indexes.sql
-- Description: Partial composite indexes for high-throughput public harvest listing
--              discovery and multi-facet filtering on Hosil Bozor.
-- Source of Truth: docs/04_domain_model.md §4.3 Table 19,
--                  docs/05_api_contract.md §3.3 Endpoints 19 & 20
-- =============================================================================

-- 1. Index for default sort order (published_at DESC, id DESC) for PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_published_discovery
ON harvest_listings (published_at DESC, id DESC)
WHERE status = 'PUBLISHED';

-- 2. Partial index for crop_type filtering on PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_crop_discovery
ON harvest_listings (crop_type, published_at DESC)
WHERE status = 'PUBLISHED';

-- 3. Partial composite index for geographic filtering (region, district) on PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_region_district
ON harvest_listings (region, district)
WHERE status = 'PUBLISHED';

-- 4. Partial index for price range queries on fixed-price PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_price_discovery
ON harvest_listings (unit_price)
WHERE status = 'PUBLISHED' AND unit_price IS NOT NULL;

-- 5. Partial index for RFQ filtering on PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_rfq_discovery
ON harvest_listings (is_rfq_enabled)
WHERE status = 'PUBLISHED';

-- 6. Partial index for quality grade filtering on PUBLISHED listings
CREATE INDEX IF NOT EXISTS idx_harvest_listings_quality_discovery
ON harvest_listings (quality_grade)
WHERE status = 'PUBLISHED';
