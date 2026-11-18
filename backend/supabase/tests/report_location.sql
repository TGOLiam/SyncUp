BEGIN;
INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data) VALUES
    ('d1000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'location-owner@example.test', '{"name":"Owner"}'),
    ('d1000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'location-outsider@example.test', '{"name":"Outsider"}');
INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
VALUES ('d2000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001', 'Location test', 'Venue', now(), now() + interval '1 day');
INSERT INTO public.zones (zone_id, event_id, name, vertices)
VALUES ('d3000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001', 'Zone', '[]');
INSERT INTO public.event_members (member_id, event_id, user_id)
VALUES ('d4000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001');
SELECT set_config('request.jwt.claim.sub', 'd1000000-0000-0000-0000-000000000001', true);
SET LOCAL ROLE authenticated;
DO $$
BEGIN
    PERFORM public.report_location('d2000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000001');
    IF NOT EXISTS (SELECT 1 FROM public.event_members WHERE member_id = 'd4000000-0000-0000-0000-000000000001'
        AND current_zone_id = 'd3000000-0000-0000-0000-000000000001' AND location_reported_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Location was not recorded';
    END IF;
END $$;
SELECT set_config('request.jwt.claim.sub', 'd1000000-0000-0000-0000-000000000002', true);
DO $$
BEGIN
    BEGIN
        PERFORM public.report_location('d2000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000001');
        RAISE EXCEPTION 'Outsider could report';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
ROLLBACK;
