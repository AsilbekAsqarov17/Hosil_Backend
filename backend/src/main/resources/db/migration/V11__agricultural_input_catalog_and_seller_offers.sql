-- =============================================================================
-- Migration: V11__agricultural_input_catalog_and_seller_offers.sql
-- Description: Master catalog products, category allowed units, seller offers,
--              and commercial agricultural-input marketplace capabilities (MKT-001, MKT-003, DEC-004, DEC-005, DEC-009).
-- Domain: Marketplace Input Catalog & Commercial Seller Offers
-- Source of Truth: docs/04_domain_model.md §4.3 (Tables 14, 15, 16, 17)
-- =============================================================================

-- 1. Reconcile categories table with Table 14 (category_type and is_active)
ALTER TABLE categories ADD COLUMN IF NOT EXISTS category_type VARCHAR(30) NOT NULL DEFAULT 'AGRICULTURAL_INPUT';
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE categories SET category_type = 'HARVEST_PRODUCE' WHERE code = 'HARVEST';
UPDATE categories SET category_type = 'AGRICULTURAL_INPUT' WHERE code IN ('FERTILIZER', 'SEEDS', 'MACHINERY');

ALTER TABLE categories DROP CONSTRAINT IF EXISTS chk_categories_type;
ALTER TABLE categories ADD CONSTRAINT chk_categories_type CHECK (category_type IN ('AGRICULTURAL_INPUT', 'HARVEST_PRODUCE'));

-- 2. Category Allowed Units (Table 15 join table per DEC-009)
CREATE TABLE IF NOT EXISTS category_allowed_units (
    category_id UUID NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_category_allowed_units PRIMARY KEY (category_id, unit_code),
    CONSTRAINT fk_allowed_units_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_allowed_units_code FOREIGN KEY (unit_code) REFERENCES units_of_measure(code) ON DELETE RESTRICT
);

-- Seed allowed units for baseline categories
INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('KG'), ('TON'), ('BAG_50KG'), ('LITER')) AS u(code)
WHERE c.code = 'FERTILIZER'
ON CONFLICT DO NOTHING;

INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('KG'), ('TON'), ('BAG_50KG'), ('PIECE')) AS u(code)
WHERE c.code = 'SEEDS'
ON CONFLICT DO NOTHING;

INSERT INTO category_allowed_units (category_id, unit_code)
SELECT c.id, u.code
FROM categories c
CROSS JOIN (VALUES ('PIECE'), ('UNIT_COUNT')) AS u(code)
WHERE c.code = 'MACHINERY' AND u.code IN (SELECT code FROM units_of_measure)
ON CONFLICT DO NOTHING;

-- 3. Master Catalog Products (Table 16 per MKT-003)
CREATE TABLE IF NOT EXISTS catalog_products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id UUID NOT NULL,
    name_uz VARCHAR(255) NOT NULL,
    name_ru VARCHAR(255) NOT NULL,
    brand_name VARCHAR(100),
    active_substance VARCHAR(255),
    default_unit_code VARCHAR(20) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_catalog_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_catalog_products_unit FOREIGN KEY (default_unit_code) REFERENCES units_of_measure(code) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_catalog_products_category ON catalog_products(category_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_catalog_products_search ON catalog_products(name_uz, brand_name);

-- 4. Seller Offers (Table 17 per DEC-005, MKT-003)
CREATE TABLE IF NOT EXISTS seller_offers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_user_id UUID NOT NULL,
    catalog_product_id UUID NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    unit_code VARCHAR(20) NOT NULL,
    available_quantity NUMERIC(14, 3) NOT NULL,
    reserved_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    min_order_quantity NUMERIC(14, 3) NOT NULL DEFAULT 1.000,
    step_quantity NUMERIC(14, 3) NOT NULL DEFAULT 1.000,
    region VARCHAR(100),
    district VARCHAR(100),
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seller_offers_seller FOREIGN KEY (seller_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_seller_offers_product FOREIGN KEY (catalog_product_id) REFERENCES catalog_products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_seller_offers_unit FOREIGN KEY (unit_code) REFERENCES units_of_measure(code) ON DELETE RESTRICT,
    CONSTRAINT chk_seller_offers_price CHECK (unit_price > 0),
    CONSTRAINT chk_seller_offers_available CHECK (available_quantity >= 0),
    CONSTRAINT chk_seller_offers_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_seller_offers_moq CHECK (min_order_quantity > 0),
    CONSTRAINT chk_seller_offers_step CHECK (step_quantity > 0),
    CONSTRAINT chk_seller_offers_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'PAUSED', 'OUT_OF_STOCK', 'ARCHIVED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_seller_offers_product_active ON seller_offers(catalog_product_id, unit_price)
    WHERE status = 'ACTIVE' AND available_quantity > 0;
CREATE INDEX IF NOT EXISTS idx_seller_offers_seller ON seller_offers(seller_user_id, status);

-- 5. Seed baseline catalog products for agricultural inputs
INSERT INTO catalog_products (id, category_id, name_uz, name_ru, brand_name, active_substance, default_unit_code, description, is_active)
SELECT
    'c0011111-0000-4000-a000-000000000001'::uuid,
    c.id,
    'Ammiakli selitra (N 34.4%)',
    'Аммиачная селитра (N 34.4%)',
    'MAXAM-CHIRCHIQ',
    'Azot (N) 34.4%',
    'BAG_50KG',
    'Qishloq xoʻjaligi ekinlari uchun yuqori samarali azotli mineral oʻgʻit.',
    TRUE
FROM categories c WHERE c.code = 'FERTILIZER'
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_products (id, category_id, name_uz, name_ru, brand_name, active_substance, default_unit_code, description, is_active)
SELECT
    'c0011111-0000-4000-a000-000000000002'::uuid,
    c.id,
    'Karbamid (Mochevina N 46.2%)',
    'Карбамид (Мочевина N 46.2%)',
    'FERGANA-AZOT',
    'Azot (N) 46.2%',
    'BAG_50KG',
    'Konsentrlangan mineral oʻgʻit, barcha turdagi tuproqlarga mos keladi.',
    TRUE
FROM categories c WHERE c.code = 'FERTILIZER'
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_products (id, category_id, name_uz, name_ru, brand_name, active_substance, default_unit_code, description, is_active)
SELECT
    'c0011111-0000-4000-a000-000000000003'::uuid,
    c.id,
    'Gʻoʻza urugʻi "Sulton" elita',
    'Семена хлопчатника "Султон" элита',
    'Oʻzbekiston Paxtachilik ITI',
    'Gossipol past nav, oʻrtapishar',
    'KG',
    'Serhosil, qurgʻoqchilikka chidamli sertifikatlangan gʻoʻza urugʻi.',
    TRUE
FROM categories c WHERE c.code = 'SEEDS'
ON CONFLICT (id) DO NOTHING;

INSERT INTO catalog_products (id, category_id, name_uz, name_ru, brand_name, active_substance, default_unit_code, description, is_active)
SELECT
    'c0011111-0000-4000-a000-000000000004'::uuid,
    c.id,
    'Tomchilatib sugʻorish lentasi 16mm (20 sm qadam)',
    'Капельная лента 16мм (шаг 20 см)',
    'AGRO-DRIP',
    'Polietilen, bosim kompensatsiyali',
    'PIECE',
    'Suvni tejovchi zamonaviy tomchilatib sugʻorish tizimi butlovchisi (boboʻz 1000m).',
    TRUE
FROM categories c WHERE c.code = 'MACHINERY'
ON CONFLICT (id) DO NOTHING;
