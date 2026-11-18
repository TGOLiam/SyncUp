-- Activate one phase atomically. Only the authenticated event creator may call it.
CREATE FUNCTION public.activate_event_phase(p_event_id uuid, p_phase_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    event_creator uuid;
    selected_sequence integer;
BEGIN
    SELECT creator_id INTO event_creator
    FROM public.events
    WHERE event_id = p_event_id
    FOR UPDATE;

    IF NOT FOUND OR auth.uid() IS NULL OR event_creator <> auth.uid() THEN
        RAISE EXCEPTION 'Event not found or action not allowed' USING ERRCODE = '42501';
    END IF;

    SELECT sequence_number INTO selected_sequence
    FROM public.phases
    WHERE event_id = p_event_id AND phase_id = p_phase_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Phase not found in event' USING ERRCODE = '22023';
    END IF;

    -- Clear the previous active phase before setting the new one.
    UPDATE public.phases
    SET status = CASE
        WHEN sequence_number < selected_sequence THEN 'COMPLETED'
        ELSE 'UPCOMING'
    END
    WHERE event_id = p_event_id;

    UPDATE public.phases
    SET status = 'ACTIVE'
    WHERE event_id = p_event_id AND phase_id = p_phase_id;

    UPDATE public.events
    SET current_phase_id = p_phase_id
    WHERE event_id = p_event_id;
END;
$$;

REVOKE ALL ON FUNCTION public.activate_event_phase(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.activate_event_phase(uuid, uuid) TO authenticated;
