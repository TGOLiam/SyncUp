BEGIN;
INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data) VALUES
    ('c1000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'send-owner@example.test', '{"name":"Owner"}'),
    ('c1000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'send-outsider@example.test', '{"name":"Outsider"}');
INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES ('c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'Send test', 'Venue', now(), now() + interval '1 day');
INSERT INTO public.channels (channel_id, event_id, name, channel_type)
VALUES ('c3000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 'Global', 'GLOBAL');
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES ('c4000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001');
SELECT set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    PERFORM public.send_text_message('c5000000-0000-0000-0000-000000000001', 'c3000000-0000-0000-0000-000000000001', 'Hello');
    PERFORM public.send_text_message('c5000000-0000-0000-0000-000000000001', 'c3000000-0000-0000-0000-000000000001', 'Hello');
    IF (SELECT count(*) FROM public.messages WHERE message_id = 'c5000000-0000-0000-0000-000000000001') <> 1 THEN
        RAISE EXCEPTION 'Retry created duplicate messages';
    END IF;
    BEGIN
        PERFORM public.send_text_message('c5000000-0000-0000-0000-000000000001', 'c3000000-0000-0000-0000-000000000001', 'Different');
        RAISE EXCEPTION 'Message ID was reused with different content';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
    BEGIN
        PERFORM public.send_text_message('c5000000-0000-0000-0000-000000000002', 'c3000000-0000-0000-0000-000000000001', '  ');
        RAISE EXCEPTION 'Blank message was accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
END $$;
SELECT set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-000000000002', true);
DO $$
BEGIN
    BEGIN
        PERFORM public.send_text_message('c5000000-0000-0000-0000-000000000003', 'c3000000-0000-0000-0000-000000000001', 'Not allowed');
        RAISE EXCEPTION 'Outsider sent a message';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
ROLLBACK;
