-- Run as the local postgres administrator. All fixtures are rolled back.
BEGIN;

INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'creator@example.test', '{"name":"Creator"}'),
    ('10000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'member@example.test', '{"name":"Member"}');

INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'Test event', 'Test venue', '2026-11-20T09:00:00Z', '2026-11-20T17:00:00Z'
);

INSERT INTO public.phases (phase_id, event_id, name, sequence_number, duration)
VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Setup', 1, '01:30'),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'Event', 2, '02:00');

INSERT INTO public.channels (channel_id, event_id, name, channel_type)
VALUES ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Global', 'GLOBAL');
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001');

INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES (
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    'Other event', 'Other venue', '2026-11-20T09:00:00Z', '2026-11-20T17:00:00Z'
);
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES ('50000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002');

-- Reject text messages without content, including NULL values.
DO $$
BEGIN
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type)
        VALUES ('60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'TEXT');
        RAISE EXCEPTION 'Missing text unexpectedly accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END;
$$;

-- Reject a message from a member of a different event.
DO $$
BEGIN
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content)
        VALUES ('60000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002', 'TEXT', 'Hello');
        RAISE EXCEPTION 'A sender from another event was unexpectedly accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END;
$$;

-- The authenticated creator can activate a phase.
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
SELECT public.activate_event_phase('20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002');
RESET ROLE;

DO $$
BEGIN
    IF (SELECT status FROM public.phases WHERE phase_id = '30000000-0000-0000-0000-000000000001') <> 'COMPLETED'
        OR (SELECT status FROM public.phases WHERE phase_id = '30000000-0000-0000-0000-000000000002') <> 'ACTIVE'
        OR (SELECT current_phase_id FROM public.events WHERE event_id = '20000000-0000-0000-0000-000000000001') <> '30000000-0000-0000-0000-000000000002'::uuid THEN
        RAISE EXCEPTION 'Phase activation did not update the event consistently';
    END IF;
END;
$$;

-- A different authenticated user cannot activate a phase.
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000002', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    IF (SELECT count(*) FROM public.events) <> 0 THEN
        RAISE EXCEPTION 'A client could read events without an access policy';
    END IF;
    BEGIN
        PERFORM public.activate_event_phase('20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001');
        RAISE EXCEPTION 'Non-creator unexpectedly activated a phase';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
END;
$$;
RESET ROLE;

DO $$
DECLARE
    unprotected_count integer;
BEGIN
    SELECT count(*) INTO unprotected_count
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname IN ('users', 'events', 'venue_layouts', 'zones', 'phases', 'channels', 'event_members', 'messages')
      AND NOT c.relrowsecurity;
    IF unprotected_count <> 0 THEN
        RAISE EXCEPTION 'A shared table does not have RLS enabled';
    END IF;
END;
$$;

ROLLBACK;
