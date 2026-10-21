-- =============================================================================
-- Migration: V16__telegram_otp_linking.sql
-- Description: Schema support for Telegram OTP delivery and contact linking flow (Milestone 6C.5, DEC-003).
-- Domain: Identity and Access Management
-- Source of Truth: docs/04_domain_model.md §4.1, docs/05_api_contract.md §3.1, docs/decision_register.md (DEC-003)
-- =============================================================================

-- Table: telegram_user_links
-- Stores verified phone-to-Telegram-chat associations established via contact sharing.
CREATE TABLE telegram_user_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(20) NOT NULL,
    telegram_chat_id BIGINT NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    telegram_username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_telegram_user_links_phone UNIQUE (phone_number),
    CONSTRAINT uq_telegram_user_links_chat UNIQUE (telegram_chat_id)
);

CREATE INDEX idx_telegram_user_links_phone ON telegram_user_links(phone_number);
CREATE INDEX idx_telegram_user_links_chat ON telegram_user_links(telegram_chat_id);

-- Table: telegram_linking_sessions
-- Short-lived, single-use sessions for deep linking bot authentication.
CREATE TABLE telegram_linking_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(20) NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL,
    telegram_chat_id BIGINT,
    telegram_user_id BIGINT,
    expires_at TIMESTAMPTZ NOT NULL,
    is_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_telegram_linking_sessions_nonce UNIQUE (nonce_hash)
);

CREATE INDEX idx_telegram_linking_sessions_nonce ON telegram_linking_sessions(nonce_hash);
CREATE INDEX idx_telegram_linking_sessions_phone ON telegram_linking_sessions(phone_number);
CREATE INDEX idx_telegram_linking_sessions_expires ON telegram_linking_sessions(expires_at);

-- Table: telegram_processed_updates
-- Idempotency tracking table to prevent re-processing updates.
CREATE TABLE telegram_processed_updates (
    update_id BIGINT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
