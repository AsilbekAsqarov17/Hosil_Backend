-- =============================================================================
-- Migration: V9__buyer_inquiries_and_rfq_negotiation.sql
-- Description: Relational schemas for wholesale harvest RFQ buyer inquiries,
--              immutable offer progression, and agreed commercial terms.
-- Source of Truth: docs/04_domain_model.md, DEC-006, docs/05_api_contract.md §3.4
-- =============================================================================

-- 1. Table: inquiries
CREATE TABLE IF NOT EXISTS inquiries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id) ON DELETE RESTRICT,
    buyer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    crop_type VARCHAR(100) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    current_offer_id UUID,
    agreed_offer_id UUID,
    agreed_at TIMESTAMPTZ,
    agreed_quantity NUMERIC(14, 3),
    agreed_unit_price NUMERIC(14, 2),
    agreed_currency VARCHAR(5),
    agreed_delivery_terms VARCHAR(255),
    rejection_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inquiries_buyer_not_seller CHECK (buyer_user_id != seller_user_id),
    CONSTRAINT chk_inquiries_status CHECK (status IN ('OPEN', 'AGREED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'))
);

-- 2. Table: inquiry_offers
CREATE TABLE IF NOT EXISTS inquiry_offers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    offer_number INT NOT NULL,
    proposer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    proposer_role VARCHAR(20) NOT NULL,
    quantity NUMERIC(14, 3) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    unit_price NUMERIC(14, 2),
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    total_price NUMERIC(14, 2),
    delivery_terms VARCHAR(255),
    notes TEXT,
    expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inquiry_offers_qty CHECK (quantity > 0),
    CONSTRAINT chk_inquiry_offers_price CHECK (unit_price IS NULL OR unit_price > 0),
    CONSTRAINT chk_inquiry_offers_role CHECK (proposer_role IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_inquiry_offers_status CHECK (status IN ('PROPOSED', 'ACCEPTED', 'SUPERSEDED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT uq_inquiry_offers_sequence UNIQUE (inquiry_id, offer_number)
);

-- 3. Foreign key constraints from inquiries to inquiry_offers
ALTER TABLE inquiries 
    ADD CONSTRAINT fk_inquiries_current_offer 
    FOREIGN KEY (current_offer_id) REFERENCES inquiry_offers(id) ON DELETE SET NULL;

ALTER TABLE inquiries 
    ADD CONSTRAINT fk_inquiries_agreed_offer 
    FOREIGN KEY (agreed_offer_id) REFERENCES inquiry_offers(id) ON DELETE SET NULL;

-- 4. Indexes for fast discovery, user dashboards, and idempotency
CREATE INDEX IF NOT EXISTS idx_inquiries_buyer ON inquiries(buyer_user_id, status);
CREATE INDEX IF NOT EXISTS idx_inquiries_seller ON inquiries(seller_user_id, status);
CREATE INDEX IF NOT EXISTS idx_inquiries_listing ON inquiries(harvest_listing_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inquiries_idempotency ON inquiries(buyer_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inquiry_offers_inquiry ON inquiry_offers(inquiry_id, offer_number);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inquiry_offers_idempotency ON inquiry_offers(inquiry_id, proposer_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
