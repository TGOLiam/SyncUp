BEGIN;
INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'lease-owner@example.test', '{"name":"Owner"}'),
    ('a1000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'lease-member@example.test', '{"name":"Member"}'),
    ('a1000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'lease-outsider@example.test', '{"name":"Outsider"}');
INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Test', 'Venue', now(), now() + interval '1 day');
INSERT INTO public.channels (channel_id, event_id, name, channel_type)
VALUES
    ('a3000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 'Global', 'GLOBAL'),
    ('a3000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000001', 'Crew', 'CUSTOM');
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES
    ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001'),
    ('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000002');

SELECT set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
DECLARE lease record;
BEGIN
    SELECT * INTO lease FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000001');
    IF NOT lease.granted OR lease.expires_at < clock_timestamp() + interval '14 seconds' THEN
        RAISE EXCEPTION 'Expected a 15-second lease';
    END IF;
    IF NOT (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000001')) THEN
        RAISE EXCEPTION 'Retry must preserve the lease';
    END IF;
END $$;
SELECT set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-000000000002', true);
DO $$
BEGIN
    IF (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000002')) THEN
        RAISE EXCEPTION 'A busy channel was granted';
    END IF;
    IF NOT (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000002', 'a5000000-0000-0000-0000-000000000002')) THEN
        RAISE EXCEPTION 'Other channels must remain available';
    END IF;
    PERFORM public.release_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000001');
    IF (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000002')) THEN
        RAISE EXCEPTION 'Another user released the lease';
    END IF;
END $$;
SELECT set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-000000000003', true);
DO $$
BEGIN
    BEGIN
        PERFORM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000003');
        RAISE EXCEPTION 'Outsider was allowed';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET ROLE;
UPDATE private.channel_speech_leases SET expires_at = clock_timestamp() - interval '1 second'
WHERE channel_id = 'a3000000-0000-0000-0000-000000000001';
SELECT set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-000000000002', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    IF NOT (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000002')) THEN
        RAISE EXCEPTION 'Expired lease must be reusable';
    END IF;
    PERFORM public.release_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000001');
    IF (SELECT granted FROM public.request_channel_speech('a3000000-0000-0000-0000-000000000001', 'a5000000-0000-0000-0000-000000000003')) THEN
        RAISE EXCEPTION 'Old token cleared a newer lease';
    END IF;
END $$;
RESET ROLE;
DO $$
BEGIN
    INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, audio_reference, audio_duration_ms)
    VALUES ('a6000000-0000-0000-0000-000000000001', 'a3000000-0000-0000-0000-000000000001',
        'a4000000-0000-0000-0000-000000000001', 'VOICE', 'test.m4a', 10000);
    BEGIN
        INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, audio_reference, audio_duration_ms)
        VALUES ('a6000000-0000-0000-0000-000000000002', 'a3000000-0000-0000-0000-000000000001',
            'a4000000-0000-0000-0000-000000000001', 'VOICE', 'test.m4a', 10001);
        RAISE EXCEPTION 'Overlong voice metadata was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
ROLLBACK;
