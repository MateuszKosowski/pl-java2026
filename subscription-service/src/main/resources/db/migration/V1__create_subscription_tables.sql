CREATE TABLE active_subscriptions (
    user_id VARCHAR(255) PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    active_from TIMESTAMP NOT NULL,
    active_until TIMESTAMP
);

CREATE TABLE token_balances (
    user_id VARCHAR(255) PRIMARY KEY,
    available_tokens INTEGER NOT NULL,
    reserved_tokens INTEGER NOT NULL,
    CONSTRAINT token_balances_available_non_negative CHECK (available_tokens >= 0),
    CONSTRAINT token_balances_reserved_non_negative CHECK (reserved_tokens >= 0),
    CONSTRAINT token_balances_subscription_fk
        FOREIGN KEY (user_id)
        REFERENCES active_subscriptions (user_id)
        ON DELETE CASCADE
);
