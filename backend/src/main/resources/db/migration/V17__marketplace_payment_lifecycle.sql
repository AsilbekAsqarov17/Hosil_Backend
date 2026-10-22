-- =============================================================================
-- Migration: V17__marketplace_payment_lifecycle.sql
-- Description: Relational schemas for provider-neutral payment transactions,
--              payment attempts, and append-only audit event stream.
-- Source of Truth: docs/04_domain_model.md §4.4 (Tables 24-25),
--                  docs/13_payment_integration_blueprint.md §6,
--                  PAY-001, PAY-002, PAY-004, DEC-007
-- =============================================================================

-- 1. Table: payments (Provider-Neutral Payment Transaction)
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    buyer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    payment_intent_reference VARCHAR(100) NOT NULL UNIQUE,
    provider_name VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(100),
    amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    idempotency_key VARCHAR(100),
    failure_reason VARCHAR(255),
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN ('INITIATED', 'PENDING_PROVIDER', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED'))
);

-- 2. Table: payment_events (Append-Only Gateway Webhook & Lifecycle Audit Log)
CREATE TABLE IF NOT EXISTS payment_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    payload_snapshot TEXT,
    external_event_id VARCHAR(100),
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Performance & Integrity Indexes
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_buyer_user_id ON payments(buyer_user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_idempotency_key ON payments(buyer_user_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_payment_events_payment_id ON payment_events(payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_events_external_id ON payment_events(external_event_id);
CREATE INDEX IF NOT EXISTS idx_payment_events_recorded_at ON payment_events(recorded_at);
