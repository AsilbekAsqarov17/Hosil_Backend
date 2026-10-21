-- =============================================================================
-- Migration: V15__seed_category_allowed_units_and_harvest.sql
-- Description: Seed category allowed units for HARVEST produce and reconcile baseline
--              category-to-unit mappings across all product lines (DEC-009, MKT-003, FARM-007).
-- Domain: Marketplace Category Allowed Units
-- Source of Truth: docs/decision_register.md (DEC-009), docs/04_domain_model.md §4.3 (Table 15)
-- =============================================================================

-- 1. Seed allowed units for HARVEST produce (KG, TON, PIECE, BAG_50KG per DEC-009)
INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('KG'), ('TON'), ('PIECE'), ('BAG_50KG')) AS u(code)
WHERE c.code = 'HARVEST'
ON CONFLICT (category_id, unit_code) DO NOTHING;

-- 2. Ensure baseline allowed units for FERTILIZER (KG, TON, BAG_50KG, LITER)
INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('KG'), ('TON'), ('BAG_50KG'), ('LITER')) AS u(code)
WHERE c.code = 'FERTILIZER'
ON CONFLICT (category_id, unit_code) DO NOTHING;

-- 3. Ensure baseline allowed units for SEEDS (KG, TON, BAG_50KG, PIECE)
INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('KG'), ('TON'), ('BAG_50KG'), ('PIECE')) AS u(code)
WHERE c.code = 'SEEDS'
ON CONFLICT (category_id, unit_code) DO NOTHING;

-- 4. Ensure baseline allowed units for MACHINERY (PIECE)
INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('PIECE')) AS u(code)
WHERE c.code = 'MACHINERY'
ON CONFLICT (category_id, unit_code) DO NOTHING;
