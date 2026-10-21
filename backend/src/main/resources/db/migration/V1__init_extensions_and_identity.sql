-- =============================================================================
-- Migration: V1__init_extensions_and_identity.sql
-- Description: Enable UUID extension and create core identity & access tables
-- Domain: Identity and Access Management
-- Source of Truth: docs/04_domain_model.md §2.1, docs/05_api_contract.md §3
-- =============================================================================

-- Extension Rationale:
-- uuid-ossp provides uuid_generate_v4() for RFC 4122 compliant UUID generation.
-- All platform primary keys use UUID v4 to support distributed generation and
-- offline client synchronization without primary key collision.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: users (Core platform identity shared across Bozor, Farm, and Logistika)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(20) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'ACTIVE',
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'uz',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')),
    CONSTRAINT chk_users_language CHECK (preferred_language IN ('uz', 'ru'))
);

CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_status ON users(status);

-- Table: user_profiles (Public and personal profile data)
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    region VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Table: user_roles (Multi-role capability per user per PRD ID-002)
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    role_name VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_roles_name CHECK (role_name IN (
        'FARMER', 'BUYER', 'SUPPLIER', 'CARRIER',
        'ADMIN_OPERATOR', 'ADMIN_SUPERVISOR', 'SUPPORT_OPERATOR'
    ))
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- Table: otp_challenges (Phone authentication challenge state machine)
CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(20) NOT NULL,
    challenge_hash VARCHAR(128) NOT NULL,
    attempts_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    expires_at TIMESTAMPTZ NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_otp_attempts CHECK (attempts_count >= 0 AND attempts_count <= max_attempts)
);

CREATE INDEX idx_otp_challenges_phone_expires ON otp_challenges(phone_number, expires_at);

-- Table: refresh_tokens (Long-lived session persistence with revocation)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Table: seller_profiles (Seller eligibility and commercial verification per DEC-004)
CREATE TABLE seller_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    seller_type VARCHAR(30) NOT NULL,
    business_name VARCHAR(150),
    inn VARCHAR(20),
    verification_status VARCHAR(25) NOT NULL DEFAULT 'UNVERIFIED',
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seller_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_seller_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_seller_profiles_type CHECK (seller_type IN ('INDIVIDUAL_FARMER', 'COMMERCIAL_SUPPLIER', 'COOPERATIVE')),
    CONSTRAINT chk_seller_profiles_status CHECK (verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX idx_seller_profiles_status ON seller_profiles(verification_status);

-- Table: carrier_profiles (Carrier fleet and verification per DEC-011)
CREATE TABLE carrier_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    company_name VARCHAR(150),
    license_number VARCHAR(50),
    verification_status VARCHAR(25) NOT NULL DEFAULT 'UNVERIFIED',
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carrier_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_carrier_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_carrier_profiles_status CHECK (verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX idx_carrier_profiles_status ON carrier_profiles(verification_status);
