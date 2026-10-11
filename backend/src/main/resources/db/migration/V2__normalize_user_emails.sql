-- Emails are stored normalized (trimmed, lower-case), so the existing unique constraint also rejects
-- case variants of the same address
UPDATE users SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)));
