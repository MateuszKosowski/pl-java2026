ALTER TABLE auth_schema.users
ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE auth_schema.users
SET role = 'ADMIN'
WHERE email = 'admin@gmail.com';

UPDATE auth_schema.users
SET role = 'USER'
WHERE email <> 'admin@gmail.com';
