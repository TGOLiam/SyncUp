-- Local integration checks; no test account or event survives the rollback.
BEGIN;

INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
VALUES
    ('91000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'creator-create@example.test', '{"name":"Creator"}'),
    ('91000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'member-create@example.test', '{"name":"Member"}');

CREATE TEMP TABLE test_result (event_id uuid, join_code text) ON COMMIT DROP;
GRANT SELECT, INSERT ON test_result TO authenticated;

SELECT set_config('request.jwt.claim.sub', '91000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
INSERT INTO test_result
SELECT * FROM public.create_event(
    '92000000-0000-0000-0000-000000000001',
    'Test event', 'Test venue', now(), now() + interval '1 day',
    '{"aspect_ratio":1.5,"boundary_vertices":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.1},{"x":0.9,"y":0.9}]}'::jsonb,
    '[{"zone_id":"93000000-0000-0000-0000-000000000001","name":"Stage","vertices":[{"x":0.2,"y":0.2},{"x":0.5,"y":0.2},{"x":0.5,"y":0.5}]}]'::jsonb,
    '[{"phase_id":"94000000-0000-0000-0000-000000000001","name":"Setup","sequence_number":1,"duration":"01:30"}]'::jsonb,
    '[{"channel_id":"95000000-0000-0000-0000-000000000001","name":"Crew","channel_type":"CUSTOM"}]'::jsonb
);

DO $$
DECLARE
    original_code text;
    repeated_code text;
BEGIN
    SELECT join_code INTO original_code FROM test_result;
    IF original_code !~ '^[0-9]{6}$' THEN
        RAISE EXCEPTION 'Invalid join code format';
    END IF;
    SELECT c.join_code INTO repeated_code FROM public.create_event(
        '92000000-0000-0000-0000-000000000001',
        'Test event', 'Test venue', now(), now() + interval '1 day',
        '{"aspect_ratio":1.5,"boundary_vertices":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.1},{"x":0.9,"y":0.9}]}'::jsonb,
        '[{"zone_id":"93000000-0000-0000-0000-000000000001","name":"Stage","vertices":[{"x":0.2,"y":0.2},{"x":0.5,"y":0.2},{"x":0.5,"y":0.5}]}]'::jsonb,
        '[{"phase_id":"94000000-0000-0000-0000-000000000001","name":"Setup","sequence_number":1,"duration":"01:30"}]'::jsonb,
        '[{"channel_id":"95000000-0000-0000-0000-000000000001","name":"Crew","channel_type":"CUSTOM"}]'::jsonb
    ) AS c;
    IF repeated_code IS DISTINCT FROM original_code THEN
        RAISE EXCEPTION 'Retry did not preserve the join code';
    END IF;
    IF public.get_event_join_code('92000000-0000-0000-0000-000000000001') IS DISTINCT FROM original_code THEN
        RAISE EXCEPTION 'Creator could not retrieve the join code';
    END IF;
    BEGIN
        PERFORM public.create_event(
            '92000000-0000-0000-0000-000000000001', 'Different name', 'Test venue',
            now(), now() + interval '1 day', NULL, '[]'::jsonb,
            '[{"phase_id":"94000000-0000-0000-0000-000000000001","name":"Setup","sequence_number":1,"duration":"01:30"}]'::jsonb,
            '[]'::jsonb
        );
        RAISE EXCEPTION 'A different request reused an existing event ID';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
    IF (SELECT count(*) FROM public.events) <> 1
        OR (SELECT count(*) FROM public.event_members) <> 1
        OR (SELECT count(*) FROM public.channels) <> 2
        OR (SELECT count(*) FROM public.zones) <> 1
        OR (SELECT count(*) FROM public.phases) <> 1 THEN
        RAISE EXCEPTION 'Event creation did not persist the expected setup';
    END IF;
END;
$$;
RESET ROLE;

-- Invalid setup must not leave an event or related records behind.
SELECT set_config('request.jwt.claim.sub', '91000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    BEGIN
        PERFORM public.create_event(
            '92000000-0000-0000-0000-000000000002', 'Invalid', 'Venue',
            now(), now() + interval '1 day', NULL, '[]'::jsonb,
            '[{"phase_id":"94000000-0000-0000-0000-000000000002","name":"Invalid","sequence_number":1,"duration":"01:99"}]'::jsonb,
            '[]'::jsonb
        );
        RAISE EXCEPTION 'Invalid phase duration was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
    IF EXISTS (SELECT 1 FROM public.events WHERE event_id = '92000000-0000-0000-0000-000000000002') THEN
        RAISE EXCEPTION 'A failed creation left an event behind';
    END IF;
END;
$$;
RESET ROLE;

-- Ten incorrect attempts are counted; the next request is limited.
SELECT set_config('request.jwt.claim.sub', '91000000-0000-0000-0000-000000000002', true);
SET LOCAL ROLE authenticated;
DO $$
DECLARE
    response text;
    code text;
BEGIN
    FOR attempt IN 1..10 LOOP
        SELECT result INTO response FROM public.join_event('invalid');
        IF response <> 'INVALID_CODE' THEN RAISE EXCEPTION 'Invalid attempt was not counted'; END IF;
    END LOOP;
    SELECT join_code INTO code FROM test_result;
    SELECT result INTO response FROM public.join_event(code);
    IF response <> 'RATE_LIMITED' THEN RAISE EXCEPTION 'Attempt limit was not enforced'; END IF;
END;
$$;
RESET ROLE;

-- Simulate the next window, then join and confirm duplicate joins are safe.
UPDATE private.join_attempts SET window_started_at = clock_timestamp() - interval '2 minutes'
WHERE user_id = '91000000-0000-0000-0000-000000000002';
SET LOCAL ROLE authenticated;
DO $$
DECLARE
    response text;
    code text;
BEGIN
    SELECT join_code INTO code FROM test_result;
    SELECT result INTO response FROM public.join_event(code);
    IF response <> 'JOINED' THEN RAISE EXCEPTION 'Valid join failed'; END IF;
    IF public.get_event_join_code('92000000-0000-0000-0000-000000000001') IS NOT NULL THEN
        RAISE EXCEPTION 'A non-creator could retrieve the join code';
    END IF;
    SELECT result INTO response FROM public.join_event(code);
    IF response <> 'ALREADY_MEMBER' THEN RAISE EXCEPTION 'Duplicate join was not detected'; END IF;
END;
$$;
RESET ROLE;

-- Once the event has ended, the same code can no longer be used.
UPDATE public.events SET start_at = now() - interval '2 hours', end_at = now() - interval '1 hour'
WHERE event_id = '92000000-0000-0000-0000-000000000001';
SET LOCAL ROLE authenticated;
DO $$
DECLARE
    response text;
    code text;
BEGIN
    SELECT join_code INTO code FROM test_result;
    SELECT result INTO response FROM public.join_event(code);
    IF response <> 'INVALID_CODE' THEN RAISE EXCEPTION 'Expired code was accepted'; END IF;
END;
$$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '91000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    IF public.get_event_join_code('92000000-0000-0000-0000-000000000001') IS NOT NULL THEN
        RAISE EXCEPTION 'Creator could retrieve an expired code';
    END IF;
END;
$$;
RESET ROLE;

ROLLBACK;
