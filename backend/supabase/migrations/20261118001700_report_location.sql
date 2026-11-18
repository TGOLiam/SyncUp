-- The server records the report time and only updates the caller's membership.
CREATE FUNCTION public.report_location(p_event_id uuid, p_zone_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
BEGIN
    IF auth.uid() IS NULL OR p_zone_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.zones WHERE event_id = p_event_id AND zone_id = p_zone_id
    ) THEN
        RAISE EXCEPTION 'Zone not found or action not allowed' USING ERRCODE = '42501';
    END IF;
    UPDATE public.event_members
    SET current_zone_id = p_zone_id, location_reported_at = clock_timestamp()
    WHERE event_id = p_event_id AND user_id = auth.uid();
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Event not found or action not allowed' USING ERRCODE = '42501';
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.report_location(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.report_location(uuid, uuid) TO authenticated;
