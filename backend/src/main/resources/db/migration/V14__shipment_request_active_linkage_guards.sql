-- ============================================================================
-- Hosil Platform Database Migration: V14
-- Description: Shipment request active linkage integrity constraints (Milestone 6B.12)
-- Companion Reference: docs/04_domain_model.md §4.5 (Table 28), LOG-001, LOG-002
-- ============================================================================

-- 1. Partial unique index: at most one active shipment request per marketplace order
CREATE UNIQUE INDEX IF NOT EXISTS uq_shipment_requests_active_order
    ON shipment_requests(order_id)
    WHERE status IN ('REQUESTED', 'QUOTED', 'ACCEPTED', 'PICKUP_ARRANGED', 'PICKED_UP', 'IN_TRANSIT')
      AND order_id IS NOT NULL;

-- 2. Partial unique index: at most one active shipment request per farm harvest batch (when not linked to an order)
CREATE UNIQUE INDEX IF NOT EXISTS uq_shipment_requests_active_batch
    ON shipment_requests(harvest_batch_id)
    WHERE status IN ('REQUESTED', 'QUOTED', 'ACCEPTED', 'PICKUP_ARRANGED', 'PICKED_UP', 'IN_TRANSIT')
      AND harvest_batch_id IS NOT NULL
      AND order_id IS NULL;
