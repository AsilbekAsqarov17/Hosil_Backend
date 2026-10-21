-- =============================================================================
-- Migration: V4__reconcile_canonical_domain_model.sql
-- Description: Forward-only schema reconciliation with authoritative domain model
--              and REST API contract for first implementation slice.
-- Source of Truth: docs/04_domain_model.md §4.1–§4.3, docs/05_api_contract.md §3
-- =============================================================================

-- 1. Identity Module: Reconcile refresh_tokens -> auth_sessions & expand user roles
ALTER TABLE refresh_tokens RENAME TO auth_sessions;
ALTER TABLE auth_sessions RENAME COLUMN revoked TO is_revoked;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS ip_address INET;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Reconcile otp_challenges attributes
ALTER TABLE otp_challenges ADD COLUMN IF NOT EXISTS salt VARCHAR(32) DEFAULT '';
ALTER TABLE otp_challenges ADD COLUMN IF NOT EXISTS is_consumed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE otp_challenges ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;

-- Reconcile user_roles check constraint to permit all canonical roles
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS chk_user_roles_name;
ALTER TABLE user_roles ADD CONSTRAINT chk_user_roles_name CHECK (role_name IN (
    'GUEST', 'REGISTERED_USER', 'FARMER', 'SELLER', 'SUPPLIER', 'BUYER',
    'CARRIER', 'ADMIN_OPERATOR', 'ADMIN_SUPERVISOR', 'SUPPORT_OPERATOR', 'ADMINISTRATOR'
));

-- 2. Farm Module: Reconcile farm_memberships and harvest_batches
ALTER TABLE farm_memberships ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE farm_memberships DROP CONSTRAINT IF EXISTS chk_farm_memberships_role;
ALTER TABLE farm_memberships ADD CONSTRAINT chk_farm_memberships_role CHECK (role IN (
    'OWNER', 'MANAGER', 'AGRONOMIST', 'WORKER'
));

-- Reconcile harvest_batches column names to match domain model Table 13 & API contract
ALTER TABLE harvest_batches RENAME COLUMN total_quantity TO recorded_quantity;
ALTER TABLE harvest_batches RENAME COLUMN available_quantity TO unallocated_quantity;
ALTER TABLE harvest_batches RENAME COLUMN unit TO unit_code;
ALTER TABLE harvest_batches DROP CONSTRAINT IF EXISTS chk_harvest_batches_total;
ALTER TABLE harvest_batches DROP CONSTRAINT IF EXISTS chk_harvest_batches_available;
ALTER TABLE harvest_batches ADD CONSTRAINT chk_harvest_batches_recorded CHECK (recorded_quantity > 0);
ALTER TABLE harvest_batches ADD CONSTRAINT chk_harvest_batches_unallocated CHECK (
    unallocated_quantity >= 0 AND unallocated_quantity <= recorded_quantity
);

-- 3. Marketplace Module: Reconcile listings -> harvest_listings (Table 19)
ALTER TABLE listings RENAME TO harvest_listings;
ALTER TABLE harvest_listings RENAME COLUMN price_amount TO unit_price;
ALTER TABLE harvest_listings RENAME COLUMN order_step_quantity TO step_quantity;
ALTER TABLE harvest_listings RENAME COLUMN quantity_available TO available_quantity;
ALTER TABLE harvest_listings RENAME COLUMN quantity_unit TO unit_code;

ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS crop_type VARCHAR(100);
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS quality_grade VARCHAR(30);
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS is_rfq_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS allocated_quantity NUMERIC(14, 3);
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS reserved_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0;
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS sold_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0;
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS district VARCHAR(100);
ALTER TABLE harvest_listings ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

-- Backfill crop_type and allocated_quantity for any pre-existing rows
UPDATE harvest_listings SET crop_type = title WHERE crop_type IS NULL;
UPDATE harvest_listings SET allocated_quantity = available_quantity WHERE allocated_quantity IS NULL;
ALTER TABLE harvest_listings ALTER COLUMN crop_type SET NOT NULL;
ALTER TABLE harvest_listings ALTER COLUMN allocated_quantity SET NOT NULL;

-- Enforce the single-row quantity balance invariant on harvest_listings
ALTER TABLE harvest_listings ADD CONSTRAINT chk_listing_quantity_balance
CHECK (allocated_quantity = (available_quantity + reserved_quantity + sold_quantity));

-- Reconcile harvest_allocations foreign key reference
ALTER TABLE harvest_allocations RENAME COLUMN listing_id TO harvest_listing_id;
ALTER TABLE harvest_allocations DROP CONSTRAINT IF EXISTS fk_allocations_listing;
ALTER TABLE harvest_allocations ADD CONSTRAINT fk_allocations_harvest_listing
    FOREIGN KEY (harvest_listing_id) REFERENCES harvest_listings(id) ON DELETE CASCADE;

-- 4. Units of measure alignment
ALTER TABLE units_of_measure ADD COLUMN IF NOT EXISTS dimension_type VARCHAR(30);
UPDATE units_of_measure SET dimension_type = category WHERE dimension_type IS NULL;
ALTER TABLE units_of_measure ADD COLUMN IF NOT EXISTS name_uz VARCHAR(50);
UPDATE units_of_measure SET name_uz = uz_name WHERE name_uz IS NULL;
ALTER TABLE units_of_measure ADD COLUMN IF NOT EXISTS name_ru VARCHAR(50);
UPDATE units_of_measure SET name_ru = ru_name WHERE name_ru IS NULL;
