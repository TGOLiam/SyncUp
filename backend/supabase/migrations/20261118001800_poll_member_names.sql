-- One authorized snapshot for the workspace's shared polling cycle.
-- Channel connection state is supplied by the client; membership is enforced
-- by the existing row-level security policies on every table read.
CREATE OR REPLACE FUNCTION public.poll_workspace(p_event_id uuid, p_channel_ids uuid[])
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    active_phase uuid;
    requested_count integer;
    valid_count integer;
BEGIN
    IF auth.uid() IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.event_members AS member
        WHERE member.event_id = p_event_id AND member.user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'Event not found or action not allowed' USING ERRCODE = '42501';
    END IF;

    IF p_channel_ids IS NULL OR array_position(p_channel_ids, NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'Invalid channels' USING ERRCODE = '22023';
    END IF;
    SELECT count(DISTINCT channel_id) INTO requested_count
    FROM unnest(p_channel_ids) AS requested(channel_id);
    IF requested_count <> cardinality(p_channel_ids) OR requested_count > 2 THEN
        RAISE EXCEPTION 'Invalid channels' USING ERRCODE = '22023';
    END IF;
    SELECT count(*) INTO valid_count
    FROM public.channels AS channel
    WHERE channel.event_id = p_event_id AND channel.channel_id = ANY(p_channel_ids);
    IF valid_count <> requested_count THEN
        RAISE EXCEPTION 'Invalid channels' USING ERRCODE = '22023';
    END IF;

    SELECT current_phase_id INTO active_phase
    FROM public.events WHERE event_id = p_event_id;

    RETURN jsonb_build_object(
        'current_phase_id', active_phase,
        'phases', COALESCE((
            SELECT jsonb_agg(to_jsonb(phase) ORDER BY phase.sequence_number)
            FROM public.phases AS phase WHERE phase.event_id = p_event_id
        ), '[]'::jsonb),
        'locations', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'member_id', member.member_id,
                'user_id', member.user_id,
                'name', (SELECT profile.name FROM public.users AS profile WHERE profile.user_id = member.user_id),
                'current_zone_id', member.current_zone_id,
                'location_reported_at', member.location_reported_at
            ) ORDER BY member.member_id)
            FROM public.event_members AS member WHERE member.event_id = p_event_id
        ), '[]'::jsonb),
        'messages', COALESCE((
            SELECT jsonb_agg(to_jsonb(recent) ORDER BY recent.sent_at, recent.message_id)
            FROM unnest(p_channel_ids) AS selected(channel_id)
            CROSS JOIN LATERAL (
                SELECT message.message_id, message.channel_id, message.sender_member_id,
                    message.text_content, message.sent_at
                FROM public.messages AS message
                WHERE message.channel_id = selected.channel_id AND message.message_type = 'TEXT'
                ORDER BY message.sent_at DESC, message.message_id DESC
                LIMIT 20
            ) AS recent
        ), '[]'::jsonb)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.poll_workspace(uuid, uuid[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.poll_workspace(uuid, uuid[]) TO authenticated;
