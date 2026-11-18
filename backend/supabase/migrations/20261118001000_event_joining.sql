-- Failed attempts are stored so the limit survives separate API requests.
CREATE TABLE private.join_attempts (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    window_started_at timestamptz NOT NULL,
    failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0)
);
REVOKE ALL ON private.join_attempts FROM PUBLIC, anon, authenticated;

-- Invalid or expired codes share a result. Returning a status instead of raising
-- preserves the failed-attempt counter when the API transaction commits.
CREATE FUNCTION public.join_event(p_code text)
RETURNS TABLE (result text, event_id uuid, member_id uuid)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor uuid := auth.uid();
    attempts private.join_attempts%ROWTYPE;
    target_event uuid;
    joined_member uuid;
    request_time timestamptz := clock_timestamp();
BEGIN
    IF actor IS NULL OR NOT EXISTS (SELECT 1 FROM public.users WHERE user_id = actor) THEN
        RAISE EXCEPTION 'Authentication and a user profile are required' USING ERRCODE = '42501';
    END IF;
    INSERT INTO private.join_attempts (user_id, window_started_at)
    VALUES (actor, request_time)
    ON CONFLICT (user_id) DO NOTHING;
    SELECT * INTO attempts FROM private.join_attempts WHERE user_id = actor FOR UPDATE;
    -- Recheck the clock after waiting for another request from this account.
    request_time := clock_timestamp();
    IF request_time >= attempts.window_started_at + interval '1 minute' THEN
        UPDATE private.join_attempts
        SET window_started_at = request_time, failed_attempts = 0
        WHERE user_id = actor;
        attempts.failed_attempts := 0;
    END IF;
    IF attempts.failed_attempts >= 10 THEN
        RETURN QUERY SELECT 'RATE_LIMITED'::text, NULL::uuid, NULL::uuid;
        RETURN;
    END IF;

    SELECT e.event_id INTO target_event
    FROM private.event_join_codes AS c
    JOIN public.events AS e ON e.event_id = c.event_id
    WHERE c.code = p_code AND e.end_at > clock_timestamp();
    IF target_event IS NULL THEN
        UPDATE private.join_attempts
        SET failed_attempts = failed_attempts + 1
        WHERE user_id = actor;
        RETURN QUERY SELECT 'INVALID_CODE'::text, NULL::uuid, NULL::uuid;
        RETURN;
    END IF;

    INSERT INTO public.event_members (member_id, event_id, user_id)
    VALUES (pg_catalog.gen_random_uuid(), target_event, actor)
    ON CONFLICT ON CONSTRAINT event_members_event_id_user_id_key DO NOTHING
    RETURNING event_members.member_id INTO joined_member;
    IF joined_member IS NULL THEN
        SELECT m.member_id INTO joined_member
        FROM public.event_members AS m
        WHERE m.event_id = target_event AND m.user_id = actor;
        RETURN QUERY SELECT 'ALREADY_MEMBER'::text, target_event, joined_member;
    ELSE
        RETURN QUERY SELECT 'JOINED'::text, target_event, joined_member;
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.join_event(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.join_event(text) TO authenticated;

-- Only the creator can retrieve a still-valid code after event creation.
CREATE FUNCTION public.get_event_join_code(p_event_id uuid)
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT c.code
    FROM private.event_join_codes AS c
    JOIN public.events AS e ON e.event_id = c.event_id
    WHERE e.event_id = p_event_id
      AND e.creator_id = (SELECT auth.uid())
      AND e.end_at > now();
$$;

REVOKE ALL ON FUNCTION public.get_event_join_code(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_event_join_code(uuid) TO authenticated;
