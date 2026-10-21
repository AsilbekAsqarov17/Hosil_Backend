-- =============================================================================
-- Migration: V10__wholesale_order_placement_and_lifecycle.sql
-- Description: Relational schemas for wholesale harvest orders, order items,
--              atomic inventory reservations, and append-only status history.
-- Source of Truth: docs/04_domain_model.md §4.3 (Table 20), §4.4 (Tables 21-23),
--                  DEC-006, DEC-010, docs/05_api_contract.md §3.5
-- =============================================================================

-- 1. Table: orders (Master Purchase Order)
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    buyer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    inquiry_id UUID REFERENCES inquiries(id) ON DELETE RESTRICT,
    agreed_offer_id UUID REFERENCES inquiry_offers(id) ON DELETE RESTRICT,
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id) ON DELETE RESTRICT,
    order_flow_type VARCHAR(30) NOT NULL DEFAULT 'HARVEST_PURCHASE_REQUEST',
    total_amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    status VARCHAR(35) NOT NULL DEFAULT 'AWAITING_SELLER_CONFIRMATION',
    delivery_type VARCHAR(30) NOT NULL DEFAULT 'BUYER_ARRANGES',
    delivery_address TEXT,
    delivery_terms VARCHAR(255),
    notes TEXT,
    rejection_reason TEXT,
    cancellation_reason TEXT,
    confirmation_expires_at TIMESTAMPTZ,
    payment_expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_buyer_not_seller CHECK (buyer_user_id != seller_user_id),
    CONSTRAINT chk_orders_flow_type CHECK (order_flow_type IN ('INPUT_DIRECT_CHECKOUT', 'HARVEST_PURCHASE_REQUEST')),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('DRAFT', 'AWAITING_SELLER_CONFIRMATION', 'PENDING_PAYMENT', 'CONFIRMED', 'PROCESSING', 'READY_FOR_DISPATCH', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED', 'DISPUTED')),
    CONSTRAINT chk_orders_delivery_type CHECK (delivery_type IN ('BUYER_ARRANGES', 'SELLER_DELIVERS', 'HOSIL_LOGISTIKA'))
);

-- 2. Table: order_items (Immutable Product Snapshot)
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id) ON DELETE RESTRICT,
    item_snapshot_title VARCHAR(255) NOT NULL,
    crop_type VARCHAR(100) NOT NULL,
    quantity NUMERIC(14, 3) NOT NULL,
    unit_code VARCHAR(20) NOT NULL REFERENCES units_of_measure(code),
    unit_price NUMERIC(14, 2) NOT NULL,
    subtotal_amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(5) NOT NULL DEFAULT 'UZS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_subtotal CHECK (subtotal_amount >= 0)
);

-- 3. Table: order_status_history (Append-Only Audit Trail)
CREATE TABLE IF NOT EXISTS order_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(35),
    to_status VARCHAR(35) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(30) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Table: inventory_reservations (Atomic Temporary Stock Hold)
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id) ON DELETE RESTRICT,
    reserved_quantity NUMERIC(14, 3) NOT NULL,
    unit_code VARCHAR(20) NOT NULL REFERENCES units_of_measure(code),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inv_reservations_qty CHECK (reserved_quantity > 0),
    CONSTRAINT chk_inv_reservations_status CHECK (status IN ('ACTIVE', 'CONFIRMED_SOLD', 'RELEASED_EXPIRED', 'RELEASED_CANCELLED'))
);

-- 5. Indexes for fast query lookup, dashboard filtering, concurrency, and idempotency
CREATE INDEX IF NOT EXISTS idx_orders_buyer ON orders(buyer_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_seller ON orders(seller_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_listing ON orders(harvest_listing_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_idempotency ON orders(buyer_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_inquiry ON orders(inquiry_id) WHERE inquiry_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_listing ON order_items(harvest_listing_id);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON order_status_history(order_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_inventory_reservations_order ON inventory_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_listing ON inventory_reservations(harvest_listing_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_active_expiry ON inventory_reservations(expires_at) WHERE status = 'ACTIVE';
