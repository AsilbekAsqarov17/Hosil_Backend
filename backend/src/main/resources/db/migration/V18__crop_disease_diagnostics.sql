-- V18: Crop Disease Diagnostics and AI Scanning Subsystem
-- Aligns with docs/01_prd.md AI-001..AI-007, docs/02_architecture.md §8.4, and docs/04_domain_model.md §2

CREATE TABLE IF NOT EXISTS crop_disease_diagnoses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    farm_id UUID REFERENCES farms(id) ON DELETE SET NULL,
    field_id UUID REFERENCES fields(id) ON DELETE SET NULL,
    crop_cycle_id UUID REFERENCES crop_cycles(id) ON DELETE SET NULL,
    crop_type VARCHAR(64) NOT NULL,
    user_notes TEXT,
    image_storage_path VARCHAR(512) NOT NULL,
    image_content_type VARCHAR(64) NOT NULL,
    image_file_size BIGINT NOT NULL,
    image_width INT,
    image_height INT,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    condition_name VARCHAR(128),
    condition_code VARCHAR(64),
    confidence_score NUMERIC(5, 4),
    is_healthy BOOLEAN,
    severity VARCHAR(32),
    agronomic_advice TEXT,
    cultural_controls TEXT,
    recommended_checks TEXT,
    model_name VARCHAR(64),
    model_version VARCHAR(32),
    is_simulated BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(128),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_diagnosis_status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'UNSUPPORTED')
    ),
    CONSTRAINT chk_diagnosis_confidence CHECK (
        confidence_score IS NULL OR (confidence_score >= 0.0 AND confidence_score <= 1.0)
    )
);

CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_user_id ON crop_disease_diagnoses(user_id);
CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_farm_id ON crop_disease_diagnoses(farm_id);
CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_field_id ON crop_disease_diagnoses(field_id);
CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_status ON crop_disease_diagnoses(status);
CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_created_at ON crop_disease_diagnoses(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crop_diagnoses_idempotency_key ON crop_disease_diagnoses(idempotency_key) WHERE idempotency_key IS NOT NULL;
