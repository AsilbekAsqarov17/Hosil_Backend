-- Hosil Ecosystem — Staging Synthetic Data Cleanup Script
-- Safely truncates transactional staging data while preserving master catalogs,
-- category taxonomies, Flyway schema history, and platform roles.
-- Target database: hosil_staging ONLY.

BEGIN;

-- 1. Reputation & Reviews
TRUNCATE TABLE review_moderation_logs CASCADE;
TRUNCATE TABLE review_reports CASCADE;
TRUNCATE TABLE review_responses CASCADE;
TRUNCATE TABLE reviews CASCADE;
TRUNCATE TABLE user_reputation_aggregates CASCADE;

-- 2. Marketplace Payments & Orders
TRUNCATE TABLE payment_events CASCADE;
TRUNCATE TABLE payments CASCADE;
TRUNCATE TABLE inventory_reservations CASCADE;
TRUNCATE TABLE order_items CASCADE;
TRUNCATE TABLE order_status_history CASCADE;
TRUNCATE TABLE orders CASCADE;

-- 3. Equipment Leasing & Applications
TRUNCATE TABLE leasing_repayment_schedules CASCADE;
TRUNCATE TABLE leasing_contracts CASCADE;
TRUNCATE TABLE leasing_application_status_history CASCADE;
TRUNCATE TABLE leasing_applications CASCADE;

-- 4. Logistics & Shipments
TRUNCATE TABLE shipment_status_history CASCADE;
TRUNCATE TABLE shipment_requests CASCADE;

-- 5. Farm Activities & Diagnoses
TRUNCATE TABLE crop_disease_diagnoses CASCADE;
TRUNCATE TABLE farm_activities CASCADE;
TRUNCATE TABLE harvest_allocations CASCADE;
TRUNCATE TABLE listing_media CASCADE;
TRUNCATE TABLE harvest_listings CASCADE;
TRUNCATE TABLE harvest_batches CASCADE;
TRUNCATE TABLE crop_cycles CASCADE;

-- 6. RFQ / Inquiries
TRUNCATE TABLE inquiry_offers CASCADE;
TRUNCATE TABLE inquiries CASCADE;

-- 7. Ephemeral Auth & Sessions
TRUNCATE TABLE otp_challenges CASCADE;
TRUNCATE TABLE telegram_linking_sessions CASCADE;
TRUNCATE TABLE auth_sessions CASCADE;

COMMIT;
