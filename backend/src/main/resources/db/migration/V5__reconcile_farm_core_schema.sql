-- =============================================================================
-- Migration: V5__reconcile_farm_core_schema.sql
-- Description: Forward-only schema reconciliation for Farm Core domain
--              (farms, farm_memberships, fields, crop_cycles)
-- Source of Truth: docs/04_domain_model.md §4.2 (Tables 8-11), docs/05_api_contract.md §3.2
-- =============================================================================

-- 1. Table: farms
ALTER TABLE farms ADD COLUMN IF NOT EXISTS legal_type VARCHAR(50) NOT NULL DEFAULT 'DEHQON';
ALTER TABLE farms DROP CONSTRAINT IF EXISTS chk_farms_legal_type;
ALTER TABLE farms ADD CONSTRAINT chk_farms_legal_type CHECK (legal_type IN ('DEHQON', 'FERMER', 'MCHJ', 'TOMORQA'));

ALTER TABLE farms RENAME COLUMN total_area TO total_area_ha;
ALTER TABLE farms ALTER COLUMN total_area_ha DROP NOT NULL;
ALTER TABLE farms DROP CONSTRAINT IF EXISTS chk_farms_area;
ALTER TABLE farms ADD CONSTRAINT chk_farms_total_area_ha CHECK (total_area_ha IS NULL OR total_area_ha > 0);

-- 2. Table: farm_memberships
ALTER TABLE farm_memberships ADD COLUMN IF NOT EXISTS permissions_mask VARCHAR(255) NOT NULL DEFAULT 'READ';

-- 3. Table: fields
ALTER TABLE fields ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE fields DROP CONSTRAINT IF EXISTS chk_fields_unit;
ALTER TABLE fields ADD CONSTRAINT chk_fields_unit CHECK (area_unit IN ('HECTARE', 'SOTKA', 'SQM'));

-- 4. Table: crop_cycles
ALTER TABLE crop_cycles ADD COLUMN IF NOT EXISTS crop_type VARCHAR(100);
ALTER TABLE crop_cycles ADD COLUMN IF NOT EXISTS variety VARCHAR(100);
ALTER TABLE crop_cycles ADD COLUMN IF NOT EXISTS actual_harvest_date DATE;
ALTER TABLE crop_cycles ALTER COLUMN crop_catalog_id DROP NOT NULL;
ALTER TABLE crop_cycles ALTER COLUMN planted_area DROP NOT NULL;
ALTER TABLE crop_cycles ALTER COLUMN expected_harvest_date DROP NOT NULL;

UPDATE crop_cycles SET crop_type = 'Pomidor' WHERE crop_type IS NULL;
ALTER TABLE crop_cycles ALTER COLUMN crop_type SET NOT NULL;

ALTER TABLE crop_cycles DROP CONSTRAINT IF EXISTS chk_crop_cycles_status;
ALTER TABLE crop_cycles ADD CONSTRAINT chk_crop_cycles_status CHECK (
    status IN ('PLANNED', 'PLANNING', 'ACTIVE', 'HARVESTED', 'COMPLETED', 'TERMINATED', 'CANCELLED', 'FAILED')
);

ALTER TABLE crop_cycles DROP CONSTRAINT IF EXISTS chk_crop_cycles_dates;
ALTER TABLE crop_cycles ADD CONSTRAINT chk_crop_cycles_dates CHECK (
    (expected_harvest_date IS NULL OR expected_harvest_date >= planting_date) AND
    (actual_harvest_date IS NULL OR actual_harvest_date >= planting_date)
);
