INSERT INTO active_subscriptions (user_id, plan_code, active_from, active_until)
VALUES
    ('1', 'PRO', CURRENT_TIMESTAMP, NULL),
    ('2', 'FREE', CURRENT_TIMESTAMP, NULL),
    ('3', 'STANDARD', CURRENT_TIMESTAMP, NULL),
    ('4', 'PRO', CURRENT_TIMESTAMP, NULL),
    ('5', 'FREE', CURRENT_TIMESTAMP, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO token_balances (user_id, available_tokens, reserved_tokens)
VALUES
    ('1', 2500, 0),
    ('2', 50, 0),
    ('3', 500, 0),
    ('4', 2500, 0),
    ('5', 3, 0)
ON CONFLICT DO NOTHING;
