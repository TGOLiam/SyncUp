-- Run against the local test database. No fixture survives the rollback.
BEGIN;

INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
VALUES (
    '70000000-0000-0000-0000-000000000001',
    'authenticated',
    'authenticated',
    'profile@example.test',
    '{"name":"  Sample User  "}'
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.users
        WHERE user_id = '70000000-0000-0000-0000-000000000001'
          AND name = 'Sample User'
          AND email = 'profile@example.test'
    ) THEN
        RAISE EXCEPTION 'Signup did not create the expected profile';
    END IF;
END;
$$;

-- Missing, blank, and non-string names must not create an Auth account.
DO $$
DECLARE
    metadata jsonb;
BEGIN
    FOR metadata IN SELECT value FROM jsonb_array_elements('[{}, {"name":"   "}, {"name":12}]'::jsonb)
    LOOP
        BEGIN
            INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
            VALUES (
                '70000000-0000-0000-0000-000000000002',
                'authenticated',
                'authenticated',
                'invalid@example.test',
                metadata
            );
            RAISE EXCEPTION 'Signup without a valid name was unexpectedly accepted';
        EXCEPTION WHEN check_violation THEN
            NULL;
        END;
    END LOOP;
    IF EXISTS (SELECT 1 FROM auth.users WHERE id = '70000000-0000-0000-0000-000000000002') THEN
        RAISE EXCEPTION 'Invalid signup left an Auth account behind';
    END IF;
END;
$$;

UPDATE auth.users
SET email = 'updated@example.test'
WHERE id = '70000000-0000-0000-0000-000000000001';

DO $$
BEGIN
    IF (SELECT email FROM public.users WHERE user_id = '70000000-0000-0000-0000-000000000001') <> 'updated@example.test' THEN
        RAISE EXCEPTION 'Profile email was not updated';
    END IF;
END;
$$;

DELETE FROM auth.users WHERE id = '70000000-0000-0000-0000-000000000001';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.users WHERE user_id = '70000000-0000-0000-0000-000000000001') THEN
        RAISE EXCEPTION 'Profile remained after account deletion';
    END IF;
END;
$$;

ROLLBACK;
