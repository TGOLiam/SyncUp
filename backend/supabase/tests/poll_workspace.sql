BEGIN;
INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data) VALUES
    ('b1000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'poll-owner@example.test', '{"name":"Owner"}'),
    ('b1000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'poll-outsider@example.test', '{"name":"Outsider"}');
INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Poll test', 'Venue', now(), now() + interval '1 day');
INSERT INTO public.channels (channel_id, event_id, name, channel_type) VALUES
    ('b3000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Global', 'GLOBAL'),
    ('b3000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000001', 'Crew', 'CUSTOM');
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES ('b4000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001');
INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content) VALUES
    ('b5000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0000-000000000001', 'b4000000-0000-0000-0000-000000000001', 'TEXT', 'Global message'),
    ('b5000000-0000-0000-0000-000000000002', 'b3000000-0000-0000-0000-000000000002', 'b4000000-0000-0000-0000-000000000001', 'TEXT', 'Crew message');
INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content, sent_at)
SELECT md5('poll-message-' || number)::uuid,
    'b3000000-0000-0000-0000-000000000002',
    'b4000000-0000-0000-0000-000000000001',
    'TEXT', 'History ' || number, now() + number * interval '1 second'
FROM generate_series(1, 25) AS number;

SELECT set_config('request.jwt.claim.sub', 'b1000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
DECLARE snapshot jsonb;
BEGIN
    snapshot := public.poll_workspace('b2000000-0000-0000-0000-000000000001', ARRAY['b3000000-0000-0000-0000-000000000001'::uuid]);
    IF jsonb_array_length(snapshot->'messages') <> 1 OR snapshot->'messages'->0->>'text_content' <> 'Global message' THEN
        RAISE EXCEPTION 'Poll included messages outside the requested channel';
    END IF;
    IF jsonb_array_length(snapshot->'locations') <> 1 OR snapshot->'phases' <> '[]'::jsonb THEN
        RAISE EXCEPTION 'Poll omitted workspace state';
    END IF;
    snapshot := public.poll_workspace('b2000000-0000-0000-0000-000000000001', ARRAY['b3000000-0000-0000-0000-000000000002'::uuid]);
    IF jsonb_array_length(snapshot->'messages') <> 20 OR snapshot->'messages'->0->>'text_content' <> 'History 6' THEN
        RAISE EXCEPTION 'Poll did not return the latest 20 messages in order';
    END IF;
    snapshot := public.poll_workspace('b2000000-0000-0000-0000-000000000001', ARRAY[]::uuid[]);
    IF snapshot->'messages' <> '[]'::jsonb THEN
        RAISE EXCEPTION 'No connected channels should return no messages';
    END IF;
    BEGIN
        PERFORM public.poll_workspace('b2000000-0000-0000-0000-000000000001', ARRAY['b3000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-000000000001'::uuid]);
        RAISE EXCEPTION 'Duplicate channels were accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
END $$;
SELECT set_config('request.jwt.claim.sub', 'b1000000-0000-0000-0000-000000000002', true);
DO $$
BEGIN
    BEGIN
        PERFORM public.poll_workspace('b2000000-0000-0000-0000-000000000001', ARRAY[]::uuid[]);
        RAISE EXCEPTION 'Outsider could poll event';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
ROLLBACK;
