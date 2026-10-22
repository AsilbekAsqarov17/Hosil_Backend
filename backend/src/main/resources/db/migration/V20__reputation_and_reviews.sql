-- =============================================================================
-- Migration: V20__reputation_and_reviews.sql
-- Description: Two-sided, transaction-verified reputation and reviews,
--              double-blind publishing window, factual rebuttals, reports,
--              moderation audit logs, and reputation aggregates.
-- Source of Truth: docs/12_reputation_and_reviews.md,
--                  docs/decision_register.md (DEC-018),
--                  PRD MKT-018, REV-001 - REV-010, ADM-005
-- =============================================================================

-- 1. Table: reviews (Transaction-Gated Counterparty and Product Reviews)
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    order_item_id UUID REFERENCES order_items(id) ON DELETE RESTRICT,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    target_listing_id UUID,
    review_type VARCHAR(30) NOT NULL,
    overall_rating SMALLINT NOT NULL,
    dimension_scores JSONB,
    commentary TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'BLIND_PENDING',
    is_counterparty_blind BOOLEAN NOT NULL DEFAULT TRUE,
    blind_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_reviews_rating CHECK (overall_rating >= 1 AND overall_rating <= 5),
    CONSTRAINT chk_reviews_commentary_len CHECK (commentary IS NULL OR LENGTH(commentary) <= 1000),
    CONSTRAINT chk_reviews_type CHECK (review_type IN ('BUYER_TO_SELLER', 'SELLER_TO_BUYER', 'LISTING_PRODUCT')),
    CONSTRAINT chk_reviews_status CHECK (status IN ('BLIND_PENDING', 'PUBLISHED', 'FLAGGED', 'HIDDEN', 'REMOVED'))
);

-- Unique index: Single review per order, author, and review type
CREATE UNIQUE INDEX IF NOT EXISTS uq_reviews_order_author_type 
ON reviews (order_id, author_id, review_type);

-- Unique index: Single product review per order, order item, and author
CREATE UNIQUE INDEX IF NOT EXISTS uq_reviews_order_item_product 
ON reviews (order_id, order_item_id, author_id) 
WHERE review_type = 'LISTING_PRODUCT';

-- Query performance indexes
CREATE INDEX IF NOT EXISTS idx_reviews_target_user_pub ON reviews (target_user_id, status) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_reviews_target_listing_pub ON reviews (target_listing_id, status) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_reviews_order_id ON reviews (order_id);
CREATE INDEX IF NOT EXISTS idx_reviews_author_id ON reviews (author_id);
CREATE INDEX IF NOT EXISTS idx_reviews_blind_until ON reviews (blind_until) WHERE status = 'BLIND_PENDING';

-- 2. Table: review_responses (Single Factual Rebuttal by Counterparty)
CREATE TABLE IF NOT EXISTS review_responses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID NOT NULL UNIQUE REFERENCES reviews(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    response_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_response_text_len CHECK (LENGTH(response_text) > 0 AND LENGTH(response_text) <= 500)
);

CREATE INDEX IF NOT EXISTS idx_review_responses_author ON review_responses (author_id);

-- 3. Table: review_reports (Abuse/Policy Flagging Queue)
CREATE TABLE IF NOT EXISTS review_reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason_code VARCHAR(50) NOT NULL,
    details TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    resolution_notes TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_reports_status CHECK (status IN ('PENDING', 'DISMISSED', 'ACTIONED'))
);

CREATE INDEX IF NOT EXISTS idx_review_reports_review_id ON review_reports (review_id);
CREATE INDEX IF NOT EXISTS idx_review_reports_status ON review_reports (status);

-- 4. Table: review_moderation_logs (Immutable Administrator Audit Trail)
CREATE TABLE IF NOT EXISTS review_moderation_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE RESTRICT,
    admin_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(50) NOT NULL,
    justification_code VARCHAR(50) NOT NULL,
    admin_notes TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_mod_logs_review_id ON review_moderation_logs (review_id);
CREATE INDEX IF NOT EXISTS idx_review_mod_logs_admin_id ON review_moderation_logs (admin_user_id);

-- 5. Table: user_reputation_aggregates (Precomputed Aggregate Scores)
CREATE TABLE IF NOT EXISTS user_reputation_aggregates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_context VARCHAR(20) NOT NULL,
    average_rating NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    total_reviews INT NOT NULL DEFAULT 0,
    total_completed_orders INT NOT NULL DEFAULT 0,
    dimension_averages JSONB,
    last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_rep_role CHECK (role_context IN ('SELLER', 'BUYER')),
    CONSTRAINT chk_user_rep_rating CHECK (average_rating >= 0.00 AND average_rating <= 5.00),
    CONSTRAINT uq_user_reputation_user_role UNIQUE (user_id, role_context)
);

CREATE INDEX IF NOT EXISTS idx_user_rep_user_id ON user_reputation_aggregates (user_id);
