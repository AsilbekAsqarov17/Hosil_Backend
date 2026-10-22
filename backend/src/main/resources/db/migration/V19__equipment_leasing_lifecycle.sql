-- ============================================================================
-- Hosil Platform Database Migration: V19
-- Description: Agricultural equipment catalogue, leasing applications, partner financing, contracts & repayment schedules
-- Companion Reference: docs/01_prd.md LEASE-001 - LEASE-005, docs/04_domain_model.md §4.5 (Table 31), DEC-013
-- ============================================================================

-- 1. Table: equipment_listings (Agricultural machinery and vehicles available for leasing/rent)
CREATE TABLE IF NOT EXISTS equipment_listings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    model_name VARCHAR(150) NOT NULL,
    manufacturer VARCHAR(150) NOT NULL,
    year_of_manufacture INT,
    condition VARCHAR(30) NOT NULL DEFAULT 'GOOD',
    description TEXT,
    specifications TEXT,
    region VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    image_urls TEXT,
    daily_rate NUMERIC(14, 2),
    monthly_rate NUMERIC(14, 2) NOT NULL,
    market_value NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UZS',
    min_down_payment_percent NUMERIC(5, 2) NOT NULL DEFAULT 20.00,
    min_term_months INT NOT NULL DEFAULT 12,
    max_term_months INT NOT NULL DEFAULT 36,
    availability_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    publication_status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_equipment_monthly_rate CHECK (monthly_rate > 0),
    CONSTRAINT chk_equipment_market_value CHECK (market_value > 0),
    CONSTRAINT chk_equipment_down_payment CHECK (min_down_payment_percent >= 0.00 AND min_down_payment_percent <= 100.00),
    CONSTRAINT chk_equipment_term CHECK (min_term_months > 0 AND max_term_months >= min_term_months),
    CONSTRAINT chk_equipment_avail CHECK (availability_status IN ('AVAILABLE', 'RESERVED', 'LEASED', 'MAINTENANCE', 'ARCHIVED')),
    CONSTRAINT chk_equipment_pub CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_equipment_category_avail ON equipment_listings(category, availability_status, publication_status);
CREATE INDEX IF NOT EXISTS idx_equipment_region ON equipment_listings(region, availability_status);
CREATE INDEX IF NOT EXISTS idx_equipment_owner ON equipment_listings(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_equipment_created_at ON equipment_listings(created_at DESC);

-- 2. Table: leasing_applications (Farmer applications for machinery financing)
CREATE TABLE IF NOT EXISTS leasing_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_number VARCHAR(50) NOT NULL,
    applicant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    equipment_id UUID NOT NULL REFERENCES equipment_listings(id) ON DELETE RESTRICT,
    farm_id UUID REFERENCES farms(id) ON DELETE SET NULL,
    applicant_business_type VARCHAR(50) NOT NULL DEFAULT 'FERMER',
    applicant_name VARCHAR(150) NOT NULL,
    applicant_phone VARCHAR(50) NOT NULL,
    desired_term_months INT NOT NULL,
    proposed_down_payment NUMERIC(14, 2) NOT NULL,
    estimated_equipment_cost NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UZS',
    intended_use_notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    partner_bank_code VARCHAR(50) DEFAULT 'MOCK_AGROBANK_SANDBOX',
    partner_application_ref VARCHAR(100),
    approved_principal NUMERIC(14, 2),
    approved_term_months INT,
    approved_down_payment NUMERIC(14, 2),
    approved_monthly_installment NUMERIC(14, 2),
    rejection_reason TEXT,
    review_notes TEXT,
    is_simulated BOOLEAN NOT NULL DEFAULT TRUE,
    disclaimer TEXT,
    idempotency_key VARCHAR(100),
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_leasing_app_number UNIQUE (application_number),
    CONSTRAINT chk_leasing_app_cost CHECK (estimated_equipment_cost > 0),
    CONSTRAINT chk_leasing_app_down_payment CHECK (proposed_down_payment >= 0),
    CONSTRAINT chk_leasing_app_term CHECK (desired_term_months > 0),
    CONSTRAINT chk_leasing_app_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_INFO_REQUESTED',
        'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED', 'CONTRACT_PENDING', 'CONTRACT_SIGNED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_leasing_app_user ON leasing_applications(applicant_user_id, status);
CREATE INDEX IF NOT EXISTS idx_leasing_app_equipment ON leasing_applications(equipment_id, status);
CREATE INDEX IF NOT EXISTS idx_leasing_app_created ON leasing_applications(created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_leasing_app_idempotency ON leasing_applications(applicant_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 3. Table: leasing_application_status_history (Auditable state timeline for compliance)
CREATE TABLE IF NOT EXISTS leasing_application_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL REFERENCES leasing_applications(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_leasing_history_app ON leasing_application_status_history(application_id, created_at ASC);

-- 4. Table: leasing_contracts (Executed or pending leasing financing agreements)
CREATE TABLE IF NOT EXISTS leasing_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_number VARCHAR(50) NOT NULL,
    application_id UUID NOT NULL REFERENCES leasing_applications(id) ON DELETE RESTRICT,
    equipment_id UUID NOT NULL REFERENCES equipment_listings(id) ON DELETE RESTRICT,
    lessee_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    lessor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    partner_bank_code VARCHAR(50) NOT NULL,
    approved_principal NUMERIC(14, 2) NOT NULL,
    approved_down_payment NUMERIC(14, 2) NOT NULL,
    annual_interest_rate NUMERIC(5, 4) NOT NULL,
    term_months INT NOT NULL,
    monthly_installment NUMERIC(14, 2) NOT NULL,
    total_repayment NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UZS',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SIGNATURE',
    signed_at TIMESTAMPTZ,
    is_simulated BOOLEAN NOT NULL DEFAULT TRUE,
    disclaimer TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_leasing_contract_number UNIQUE (contract_number),
    CONSTRAINT chk_contract_status CHECK (status IN (
        'PENDING_SIGNATURE', 'ACTIVE', 'COMPLETED', 'DEFAULTED', 'TERMINATED'
    )),
    CONSTRAINT chk_contract_amounts CHECK (approved_principal > 0 AND total_repayment >= approved_principal),
    CONSTRAINT chk_contract_dates CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_contract_lessee ON leasing_contracts(lessee_user_id, status);
CREATE INDEX IF NOT EXISTS idx_contract_equipment ON leasing_contracts(equipment_id);
-- Strict partial unique index preventing concurrent active lease contracts on the same equipment
CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_active_contract ON leasing_contracts(equipment_id)
    WHERE status IN ('PENDING_SIGNATURE', 'ACTIVE');

-- 5. Table: leasing_repayment_schedules (Installment breakdown & payment tracking)
CREATE TABLE IF NOT EXISTS leasing_repayment_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES leasing_contracts(id) ON DELETE CASCADE,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    principal_amount NUMERIC(14, 2) NOT NULL,
    interest_amount NUMERIC(14, 2) NOT NULL,
    total_installment NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UZS',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    payment_reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contract_installment UNIQUE (contract_id, installment_number),
    CONSTRAINT chk_repayment_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE'))
);

CREATE INDEX IF NOT EXISTS idx_repayment_contract ON leasing_repayment_schedules(contract_id, installment_number ASC);
CREATE INDEX IF NOT EXISTS idx_repayment_due ON leasing_repayment_schedules(due_date, status);
