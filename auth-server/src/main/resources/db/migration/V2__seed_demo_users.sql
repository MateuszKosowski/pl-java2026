INSERT INTO auth_schema.users (id, username, email, password)
VALUES
    (2, 'free', 'free@gmail.com', '$2a$12$GnBrsRQ3rJEKSz3O6Z9Pl..49iChbchxICFGE3stXc70rOu9PjvK.'),
    (3, 'standard', 'standard@gmail.com', '$2a$12$4ZbXgiP6ckL4tWIWrgpffOJ8jZ9TsuA1UEkCDG.x4q0.LY1n/VMCy'),
    (4, 'pro', 'pro@gmail.com', '$2a$12$e63lbVXXLJ/EwivD14Nfeux9mAk9MX8i7Bs1HlhKpowZkR7YEBydS'),
    (5, 'lowbalance', 'lowbalance@gmail.com', '$2a$12$hlNoant3HPcv0/UrsT/Nj.nQO7bl2e5kuNpWN2nKRNn3KfeU2RiPG')
ON CONFLICT DO NOTHING;

-- Reset the sequence to the maximum id value to avoid conflicts with future inserts
SELECT setval(
    pg_get_serial_sequence('auth_schema.users', 'id'),
    (SELECT MAX(id) FROM auth_schema.users)
);
