ALTER TABLE users ADD COLUMN document_number VARCHAR(8);
CREATE UNIQUE INDEX ux_users_document_number ON users(document_number) WHERE document_number IS NOT NULL;

CREATE TABLE company_subscriptions (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id),
    stripe_customer_id VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255) UNIQUE,
    plan VARCHAR(20) NOT NULL CHECK (plan IN ('PLUS', 'CUSTOM')),
    seat_limit INTEGER NOT NULL CHECK (seat_limit > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'CANCELLED')),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_plus_seat_limit CHECK (plan <> 'PLUS' OR seat_limit = 3)
);

INSERT INTO company_subscriptions (company_id, plan, seat_limit, status, updated_at)
VALUES (1, 'PLUS', 3, 'ACTIVE', CURRENT_TIMESTAMP);

CREATE TABLE user_invitations (
    id VARCHAR(36) PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    site_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    document_number VARCHAR(8) NOT NULL CHECK (document_number ~ '^[0-9]{8}$'),
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    delivery_attempt INTEGER NOT NULL,
    resend_email_id VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    accepted_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_invitation_site_company FOREIGN KEY (site_id, company_id) REFERENCES sites(id, company_id)
);

CREATE INDEX idx_user_invitations_company_status ON user_invitations(company_id, status, expires_at);
CREATE INDEX idx_user_invitations_email ON user_invitations(email);
CREATE INDEX idx_user_invitations_document ON user_invitations(document_number);
