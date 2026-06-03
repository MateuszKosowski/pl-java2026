CREATE TABLE subscription_schema.token_reservations (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    tokens INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    external_operation_id VARCHAR(255),
    CONSTRAINT token_reservations_tokens_non_negative CHECK (tokens >= 0),
    CONSTRAINT token_reservations_balance_fk
        FOREIGN KEY (user_id)
        REFERENCES subscription_schema.token_balances (user_id)
        ON DELETE CASCADE
);

CREATE INDEX token_reservations_user_id_idx ON subscription_schema.token_reservations (user_id);
CREATE INDEX token_reservations_external_operation_id_idx ON subscription_schema.token_reservations (external_operation_id);
