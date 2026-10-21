-- =============================================================================
-- Migration: V3__marketplace_bridge_schema.sql
-- Description: Unit catalogs, marketplace categories, listings, and harvest allocation ledger
-- Domain: Marketplace Publication Bridge
-- Source of Truth: docs/04_domain_model.md §2.3, docs/05_api_contract.md §5
-- =============================================================================

-- Table: units_of_measure (Standardized catalog per DEC-009)
CREATE TABLE units_of_measure (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    uz_name VARCHAR(50) NOT NULL,
    ru_name VARCHAR(50) NOT NULL,
    base_conversion_factor NUMERIC(14, 6) NOT NULL DEFAULT 1.0,
    is_base_unit BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_units_of_measure_code UNIQUE (code),
    CONSTRAINT chk_units_category CHECK (category IN ('MASS', 'VOLUME', 'UNIT_COUNT', 'PACKAGING'))
);

-- Seed Baseline Units
INSERT INTO units_of_measure (code, category, uz_name, ru_name, base_conversion_factor, is_base_unit) VALUES
('KG', 'MASS', 'Kilogram', 'Килограмм', 1.0, TRUE),
('TON', 'MASS', 'Tonna', 'Тонна', 1000.0, FALSE),
('PIECE', 'UNIT_COUNT', 'Dona', 'Штука', 1.0, TRUE),
('LITER', 'VOLUME', 'Litr', 'Литр', 1.0, TRUE),
('BAG_50KG', 'PACKAGING', 'Qop (50 kg)', 'Мешок (50 кг)', 50.0, FALSE);

-- Table: categories (Hierarchical taxonomy: Harvests, Fertilizers, Seeds, Machinery)
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id UUID,
    code VARCHAR(50) NOT NULL,
    uz_name VARCHAR(100) NOT NULL,
    ru_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- Seed Baseline Root Categories
INSERT INTO categories (code, uz_name, ru_name, sort_order) VALUES
('HARVEST', 'Hosil (Qishloq xoʻjaligi mahsulotlari)', 'Урожай (Сельхозпродукция)', 1),
('FERTILIZER', 'Oʻgʻitlar va kimyoviy preparatlar', 'Удобрения и агрохимия', 2),
('SEEDS', 'Urugʻlar va koʻchatlar', 'Семена и саженцы', 3),
('MACHINERY', 'Qishloq xoʻjaligi texnikasi va qurollari', 'Сельхозтехника и инвентарь', 4);

-- Table: listings (Marketplace listings for wholesale harvests and commercial inputs)
CREATE TABLE listings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_user_id UUID NOT NULL,
    harvest_batch_id UUID,
    category_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    price_amount NUMERIC(14, 2),
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    pricing_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    quantity_available NUMERIC(14, 3) NOT NULL,
    quantity_unit VARCHAR(20) NOT NULL,
    min_order_quantity NUMERIC(14, 3) NOT NULL,
    order_step_quantity NUMERIC(14, 3) NOT NULL,
    region VARCHAR(50) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_listings_harvest_batch FOREIGN KEY (harvest_batch_id) REFERENCES harvest_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_listings_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT chk_listings_pricing_type CHECK (pricing_type IN ('FIXED', 'RFQ')),
    CONSTRAINT chk_listings_quantity CHECK (quantity_available >= 0),
    CONSTRAINT chk_listings_moq CHECK (min_order_quantity > 0),
    CONSTRAINT chk_listings_step CHECK (order_step_quantity > 0),
    CONSTRAINT chk_listings_status CHECK (status IN (
        'DRAFT', 'PENDING_MODERATION', 'PUBLISHED', 'PAUSED', 'ARCHIVED', 'SUSPENDED'
    ))
);

CREATE INDEX idx_listings_seller ON listings(seller_user_id);
CREATE INDEX idx_listings_category ON listings(category_id);
CREATE INDEX idx_listings_status_region ON listings(status, region);
CREATE INDEX idx_listings_batch ON listings(harvest_batch_id);

-- Table: harvest_allocations (Explicit allocation ledger per DEC-008)
CREATE TABLE harvest_allocations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    harvest_batch_id UUID NOT NULL,
    listing_id UUID NOT NULL,
    allocated_quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_allocations_batch FOREIGN KEY (harvest_batch_id) REFERENCES harvest_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_allocations_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT chk_allocations_quantity CHECK (allocated_quantity > 0),
    CONSTRAINT chk_allocations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'FULFILLED'))
);

CREATE INDEX idx_allocations_batch ON harvest_allocations(harvest_batch_id);
CREATE INDEX idx_allocations_listing ON harvest_allocations(listing_id);
