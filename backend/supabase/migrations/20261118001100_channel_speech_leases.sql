-- A request ID also acts as the lease token, so delayed releases cannot clear a newer lease.
CREATE TABLE private.channel_speech_leases (
    channel_id uuid PRIMARY KEY REFERENCES public.channels(channel_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    lease_token uuid NOT NULL,
    granted_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);
REVOKE ALL ON private.channel_speech_leases FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.request_channel_speech(p_channel_id uuid, p_request_id uuid)
RETURNS TABLE (granted boolean, expires_at timestamptz)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor uuid := auth.uid();
    current_lease private.channel_speech_leases%ROWTYPE;
    request_time timestamptz;
BEGIN
    IF actor IS NULL OR p_request_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.channels AS c
        JOIN public.event_members AS m ON m.event_id = c.event_id
        WHERE c.channel_id = p_channel_id AND m.user_id = actor
    ) THEN
        RAISE EXCEPTION 'Channel not found or action not allowed' USING ERRCODE = '42501';
    END IF;

    -- Locking the channel serializes competing requests, including the first one.
    PERFORM 1 FROM public.channels WHERE channel_id = p_channel_id FOR UPDATE;
    request_time := clock_timestamp();
    SELECT * INTO current_lease FROM private.channel_speech_leases
    WHERE channel_id = p_channel_id;
    IF FOUND AND current_lease.expires_at > request_time THEN
        IF current_lease.user_id = actor AND current_lease.lease_token = p_request_id THEN
            RETURN QUERY SELECT true, current_lease.expires_at;
        ELSE
            RETURN QUERY SELECT false, NULL::timestamptz;
        END IF;
        RETURN;
    END IF;

    INSERT INTO private.channel_speech_leases (channel_id, user_id, lease_token, granted_at, expires_at)
    VALUES (p_channel_id, actor, p_request_id, request_time, request_time + interval '15 seconds')
    ON CONFLICT (channel_id) DO UPDATE
    SET user_id = EXCLUDED.user_id, lease_token = EXCLUDED.lease_token,
        granted_at = EXCLUDED.granted_at, expires_at = EXCLUDED.expires_at;
    RETURN QUERY SELECT true, request_time + interval '15 seconds';
END;
$$;

CREATE FUNCTION public.release_channel_speech(p_channel_id uuid, p_request_id uuid)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $$
    DELETE FROM private.channel_speech_leases
    WHERE channel_id = p_channel_id AND user_id = (SELECT auth.uid())
      AND lease_token = p_request_id;
$$;

REVOKE ALL ON FUNCTION public.request_channel_speech(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.release_channel_speech(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_channel_speech(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.release_channel_speech(uuid, uuid) TO authenticated;
