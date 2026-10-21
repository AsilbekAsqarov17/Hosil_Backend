-- =============================================================================
-- Migration: V2__farm_core_schema.sql
-- Description: Farm management, fields, crops, activities, and harvest batches
-- Domain: Farm Operations & Agronomic History
-- Source of Truth: docs/04_domain_model.md §2.2, docs/05_api_contract.md §4
-- =============================================================================

-- Table: farms (Agricultural enterprise profile)
CREATE TABLE farms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    region VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    total_area NUMERIC(10, 2) NOT NULL,
    area_unit VARCHAR(20) NOT NULL DEFAULT 'HECTARE',
    soil_type VARCHAR(50),
    irrigation_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farms_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_farms_area CHECK (total_area > 0),
    CONSTRAINT chk_farms_unit CHECK (area_unit IN ('HECTARE', 'SOTKA'))
);

CREATE INDEX idx_farms_owner ON farms(owner_user_id);
CREATE INDEX idx_farms_region_district ON farms(region, district);

-- Table: farm_memberships (ABAC authorization for multi-user farms)
CREATE TABLE farm_memberships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    farm_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_farm_memberships_farm_user UNIQUE (farm_id, user_id),
    CONSTRAINT fk_farm_memberships_farm FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    CONSTRAINT fk_farm_memberships_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_farm_memberships_role CHECK (role IN ('OWNER', 'AGRONOMIST', 'WORKER'))
);

CREATE INDEX idx_farm_memberships_user ON farm_memberships(user_id);

-- Table: fields (Physical land parcels belonging to a farm)
CREATE TABLE fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    farm_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    area NUMERIC(10, 2) NOT NULL,
    area_unit VARCHAR(20) NOT NULL DEFAULT 'HECTARE',
    boundary_geojson TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fields_farm FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    CONSTRAINT chk_fields_area CHECK (area > 0),
    CONSTRAINT chk_fields_unit CHECK (area_unit IN ('HECTARE', 'SOTKA'))
);

CREATE INDEX idx_fields_farm ON fields(farm_id);

-- Table: crop_catalog (Standardized reference catalog of crop varieties)
CREATE TABLE crop_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL,
    uz_name VARCHAR(100) NOT NULL,
    ru_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    typical_growing_days INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_crop_catalog_code UNIQUE (code),
    CONSTRAINT chk_crop_catalog_days CHECK (typical_growing_days > 0)
);

CREATE INDEX idx_crop_catalog_category ON crop_catalog(category);

-- Table: crop_cycles (Seasonal cultivation lifecycle on a specific field)
CREATE TABLE crop_cycles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    farm_id UUID NOT NULL,
    field_id UUID NOT NULL,
    crop_catalog_id UUID NOT NULL,
    planting_date DATE NOT NULL,
    expected_harvest_date DATE NOT NULL,
    planted_area NUMERIC(10, 2) NOT NULL,
    area_unit VARCHAR(20) NOT NULL DEFAULT 'HECTARE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_crop_cycles_farm FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    CONSTRAINT fk_crop_cycles_field FOREIGN KEY (field_id) REFERENCES fields(id) ON DELETE RESTRICT,
    CONSTRAINT fk_crop_cycles_crop FOREIGN KEY (crop_catalog_id) REFERENCES crop_catalog(id) ON DELETE RESTRICT,
    CONSTRAINT chk_crop_cycles_area CHECK (planted_area > 0),
    CONSTRAINT chk_crop_cycles_dates CHECK (expected_harvest_date >= planting_date),
    CONSTRAINT chk_crop_cycles_status CHECK (status IN ('PLANNING', 'ACTIVE', 'HARVESTED', 'TERMINATED'))
);

CREATE INDEX idx_crop_cycles_farm ON crop_cycles(farm_id);
CREATE INDEX idx_crop_cycles_field ON crop_cycles(field_id);
CREATE INDEX idx_crop_cycles_status ON crop_cycles(status);

-- Table: farm_activities (Daily offline-capable logs: irrigation, fertilizing, etc.)
CREATE TABLE farm_activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    farm_id UUID NOT NULL,
    crop_cycle_id UUID,
    field_id UUID NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    activity_date TIMESTAMPTZ NOT NULL,
    quantity NUMERIC(14, 3),
    unit VARCHAR(20),
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farm_activities_farm FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    CONSTRAINT fk_farm_activities_cycle FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles(id) ON DELETE SET NULL,
    CONSTRAINT fk_farm_activities_field FOREIGN KEY (field_id) REFERENCES fields(id) ON DELETE RESTRICT,
    CONSTRAINT fk_farm_activities_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_farm_activities_type CHECK (activity_type IN (
        'PLANTING', 'IRRIGATION', 'FERTILIZING', 'PESTICIDE',
        'WEEDING', 'SCOUTING', 'HARVESTING', 'OTHER'
    ))
);

CREATE INDEX idx_farm_activities_cycle ON farm_activities(crop_cycle_id);
CREATE INDEX idx_farm_activities_farm_date ON farm_activities(farm_id, activity_date DESC);

-- Table: harvest_batches (Recorded yield batches ready for storage or marketplace publication)
CREATE TABLE harvest_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    farm_id UUID NOT NULL,
    crop_cycle_id UUID,
    harvest_date DATE NOT NULL,
    total_quantity NUMERIC(14, 3) NOT NULL,
    available_quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quality_grade VARCHAR(25),
    storage_notes TEXT,
    status VARCHAR(25) NOT NULL DEFAULT 'RECORDED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_harvest_batches_farm FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
    CONSTRAINT fk_harvest_batches_cycle FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles(id) ON DELETE RESTRICT,
    CONSTRAINT chk_harvest_batches_total CHECK (total_quantity > 0),
    CONSTRAINT chk_harvest_batches_available CHECK (available_quantity >= 0 AND available_quantity <= total_quantity),
    CONSTRAINT chk_harvest_batches_grade CHECK (quality_grade IS NULL OR quality_grade IN ('PREMIUM', 'FIRST_GRADE', 'SECOND_GRADE', 'PROCESSING')),
    CONSTRAINT chk_harvest_batches_status CHECK (status IN ('RECORDED', 'PARTIALLY_LISTED', 'FULLY_LISTED', 'DEPLETED'))
);

-- Architectural Invariant Note:
-- The CHECK constraint (available_quantity <= total_quantity) protects single-row integrity.
-- Cross-row quantity conservation (sum of harvest_allocations + available_quantity == total_quantity)
-- requires transactional enforcement with row-level pessimistic locking in the Java application layer.

CREATE INDEX idx_harvest_batches_farm ON harvest_batches(farm_id);
CREATE INDEX idx_harvest_batches_status ON harvest_batches(status);
