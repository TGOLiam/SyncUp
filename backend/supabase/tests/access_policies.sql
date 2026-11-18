-- All test users and records are temporary and are rolled back.
BEGIN;

INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
VALUES
    ('81000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'creator@example.test', '{"name":"Creator"}'),
    ('81000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'member@example.test', '{"name":"Member"}'),
    ('81000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'outsider@example.test', '{"name":"Outsider"}');

INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES
    ('82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001', 'First', 'Venue', '2026-11-20T09:00Z', '2026-11-20T17:00Z'),
    ('82000000-0000-0000-0000-000000000002', '81000000-0000-0000-0000-000000000003', 'Other', 'Venue', '2026-11-20T09:00Z', '2026-11-20T17:00Z');

INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES
    ('83000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001'),
    ('83000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000002'),
    ('83000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000002', '81000000-0000-0000-0000-000000000003');

INSERT INTO public.channels (channel_id, event_id, name, channel_type)
VALUES
    ('84000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', 'Global', 'GLOBAL'),
    ('84000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000001', 'Crew', 'CUSTOM'),
    ('84000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000002', 'Other', 'GLOBAL');

SELECT set_config('request.jwt.claim.sub', '81000000-0000-0000-0000-000000000002', true);
SET LOCAL ROLE authenticated;

DO $$
DECLARE
    changed integer;
BEGIN
    IF (SELECT count(*) FROM public.events) <> 1 THEN
        RAISE EXCEPTION 'Member did not see exactly their event';
    END IF;
    IF (SELECT count(*) FROM public.channels) <> 2 THEN
        RAISE EXCEPTION 'Member did not see both event channels';
    END IF;
    IF (SELECT count(*) FROM public.event_members) <> 2 THEN
        RAISE EXCEPTION 'Member did not see the event team';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.users WHERE email = 'creator@example.test')
        OR EXISTS (SELECT 1 FROM public.users WHERE email = 'outsider@example.test') THEN
        RAISE EXCEPTION 'User profile visibility is incorrect';
    END IF;

    UPDATE public.event_members
    SET connected_channel_id = '84000000-0000-0000-0000-000000000002'
    WHERE member_id = '83000000-0000-0000-0000-000000000002';
    GET DIAGNOSTICS changed = ROW_COUNT;
    IF changed <> 1 THEN
        RAISE EXCEPTION 'Member could not connect to their event channel';
    END IF;

    UPDATE public.event_members
    SET connected_channel_id = NULL
    WHERE member_id = '83000000-0000-0000-0000-000000000001';
    GET DIAGNOSTICS changed = ROW_COUNT;
    IF changed <> 0 THEN
        RAISE EXCEPTION 'Member could change another member presence';
    END IF;

    BEGIN
        UPDATE public.event_members
        SET connected_channel_id = '84000000-0000-0000-0000-000000000001'
        WHERE member_id = '83000000-0000-0000-0000-000000000002';
        RAISE EXCEPTION 'Global was accepted as an operational connection';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        UPDATE public.events SET name = 'Modified';
        RAISE EXCEPTION 'Event setup was unexpectedly editable';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    BEGIN
        UPDATE public.event_members SET user_id = '81000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'Member identity was unexpectedly editable';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
END;
$$;

INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content)
VALUES ('85000000-0000-0000-0000-000000000001', '84000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 'TEXT', 'Hello');

DO $$
BEGIN
    IF (SELECT count(*) FROM public.messages) <> 1 THEN
        RAISE EXCEPTION 'Member could not read the event message';
    END IF;
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content)
        VALUES ('85000000-0000-0000-0000-000000000002', '84000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000001', 'TEXT', 'Spoof');
        RAISE EXCEPTION 'Sender impersonation was unexpectedly accepted';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, audio_reference, audio_duration_ms)
        VALUES ('85000000-0000-0000-0000-000000000003', '84000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 'VOICE', 'unprotected-file', 1000);
        RAISE EXCEPTION 'Voice messages were accepted before storage policies';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content, sent_at)
        VALUES ('85000000-0000-0000-0000-000000000004', '84000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 'TEXT', 'Backdated', '2000-01-01T00:00Z');
        RAISE EXCEPTION 'A client supplied its own message timestamp';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
END;
$$;

RESET ROLE;
SELECT set_config('request.jwt.claim.sub', '81000000-0000-0000-0000-000000000003', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    IF (SELECT count(*) FROM public.messages) <> 0
        OR EXISTS (SELECT 1 FROM public.users WHERE email = 'creator@example.test') THEN
        RAISE EXCEPTION 'A non-member could access another event';
    END IF;
END;
$$;
RESET ROLE;

SET LOCAL ROLE anon;
DO $$
BEGIN
    BEGIN
        PERFORM 1 FROM public.events;
        RAISE EXCEPTION 'Anonymous access was unexpectedly allowed';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;
END;
$$;
RESET ROLE;

ROLLBACK;
