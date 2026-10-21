-- =============================================================================
-- Migration: V6__reconcile_farm_activities_schema.sql
-- Description: Forward-only schema reconciliation for farm_activities
--              supporting offline synchronization, client mutation tracking,
--              and canonical domain activity types.
-- Source of Truth: docs/04_domain_model.md §4.2 Table 12, docs/05_api_contract.md §3.2,
--                  docs/07_offline_strategy.md §3-§4, docs/api/openapi-v1.yaml
-- =============================================================================

-- 1. Reconcile column names to canonical domain model
ALTER TABLE farm_activities RENAME COLUMN activity_date TO activity_timestamp;
ALTER TABLE farm_activities RENAME COLUMN created_by_user_id TO recorded_by_user_id;

-- 2. Add synchronization metadata columns
ALTER TABLE farm_activities ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE farm_activities ADD COLUMN IF NOT EXISTS client_mutation_id VARCHAR(100);

-- 3. Update activity_type check constraint to canonical + legacy types
ALTER TABLE farm_activities DROP CONSTRAINT IF EXISTS chk_farm_activities_type;
ALTER TABLE farm_activities ADD CONSTRAINT chk_farm_activities_type CHECK (activity_type IN (
    'PLANTING', 'IRRIGATION', 'FERTILIZING', 'CHEMICAL_TREATMENT', 'PESTICIDE',
    'WEEDING', 'PRUNING', 'INSPECTION', 'SCOUTING', 'HARVEST', 'HARVESTING', 'OTHER'
));

-- 4. Quantity non-negative check constraint
ALTER TABLE farm_activities DROP CONSTRAINT IF EXISTS chk_farm_activities_quantity;
ALTER TABLE farm_activities ADD CONSTRAINT chk_farm_activities_quantity CHECK (
    quantity IS NULL OR quantity >= 0
);

-- 5. Indexes for fast mutation lookups and chronological ordering
CREATE INDEX IF NOT EXISTS idx_farm_activities_client_mutation ON farm_activities(farm_id, client_mutation_id);
CREATE INDEX IF NOT EXISTS idx_farm_activities_farm_timestamp ON farm_activities(farm_id, activity_timestamp DESC);
