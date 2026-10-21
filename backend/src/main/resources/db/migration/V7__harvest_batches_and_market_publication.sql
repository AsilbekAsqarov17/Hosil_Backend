-- =============================================================================
-- Migration: V7__harvest_batches_and_market_publication.sql
-- Description: Schema reconciliation for harvest batches, atomic market publication,
--              concurrency indexes, and listing media.
-- Source of Truth: docs/04_domain_model.md §4.2 Table 13, §4.3 Tables 18-19,
--                  docs/05_api_contract.md §3.2 (Endpoints 16, 17) & §3.3 (Endpoint 18)
-- =============================================================================

-- 1. Farm Activities: Enforce database-level uniqueness for clientMutationId per farm
DROP INDEX IF EXISTS idx_farm_activities_client_mutation;
CREATE UNIQUE INDEX IF NOT EXISTS uq_farm_activities_client_mutation 
ON farm_activities(farm_id, client_mutation_id) 
WHERE client_mutation_id IS NOT NULL;

-- 2. Harvest Batches: Align with Table 13 and API Endpoints 16-17
ALTER TABLE harvest_batches ADD COLUMN IF NOT EXISTS crop_type VARCHAR(100);
UPDATE harvest_batches SET crop_type = 'Pomidor' WHERE crop_type IS NULL;
ALTER TABLE harvest_batches ALTER COLUMN crop_type SET NOT NULL;

ALTER TABLE harvest_batches ADD COLUMN IF NOT EXISTS client_batch_id VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_harvest_batches_client_batch_id 
ON harvest_batches(farm_id, client_batch_id) 
WHERE client_batch_id IS NOT NULL;

ALTER TABLE harvest_batches ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_harvest_batches_idempotency 
ON harvest_batches(farm_id, idempotency_key) 
WHERE idempotency_key IS NOT NULL;

ALTER TABLE harvest_batches ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE harvest_batches ADD COLUMN IF NOT EXISTS storage_location_notes VARCHAR(255);
UPDATE harvest_batches SET storage_location_notes = storage_notes WHERE storage_location_notes IS NULL;

-- Reconcile quality_grade check constraint
ALTER TABLE harvest_batches DROP CONSTRAINT IF EXISTS chk_harvest_batches_grade;
ALTER TABLE harvest_batches ADD CONSTRAINT chk_harvest_batches_grade CHECK (
    quality_grade IS NULL OR quality_grade IN (
        'PREMIUM', 'FIRST_CLASS', 'SECOND_CLASS', 'STANDARD', 'INDUSTRIAL',
        'FIRST_GRADE', 'SECOND_GRADE', 'PROCESSING'
    )
);

-- Reconcile status check constraint
ALTER TABLE harvest_batches DROP CONSTRAINT IF EXISTS chk_harvest_batches_status;
ALTER TABLE harvest_batches ADD CONSTRAINT chk_harvest_batches_status CHECK (
    status IN (
        'RECORDED', 'PARTIALLY_ALLOCATED', 'FULLY_ALLOCATED',
        'PARTIALLY_LISTED', 'FULLY_LISTED', 'DEPLETED', 'ARCHIVED'
    )
);

-- 3. Harvest Listings: Market publication reconciliation
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_harvest_listings_idempotency 
ON harvest_listings(seller_user_id, idempotency_key) 
WHERE idempotency_key IS NOT NULL;

ALTER TABLE harvest_listings DROP CONSTRAINT IF EXISTS chk_listings_status;
ALTER TABLE harvest_listings ADD CONSTRAINT chk_listings_status CHECK (
    status IN ('DRAFT', 'PENDING_MODERATION', 'PUBLISHED', 'PAUSED', 'SOLD_OUT', 'ARCHIVED', 'SUSPENDED')
);

-- 4. Harvest Allocations: Ledger reconciliation
ALTER TABLE harvest_allocations ADD COLUMN IF NOT EXISTS allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_harvest_allocations_listing ON harvest_allocations(harvest_listing_id);

-- 5. Listing Media Table
CREATE TABLE IF NOT EXISTS listing_media (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id) ON DELETE CASCADE,
    media_file_id UUID NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_listing_media_listing ON listing_media(harvest_listing_id);
