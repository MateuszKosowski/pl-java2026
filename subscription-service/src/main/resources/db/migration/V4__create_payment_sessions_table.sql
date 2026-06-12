CREATE TABLE subscription_schema.payment_sessions (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    target_plan VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
