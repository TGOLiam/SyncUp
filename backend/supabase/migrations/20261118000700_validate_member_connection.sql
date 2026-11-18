-- Global is always available and is not the member's operational connection.
CREATE FUNCTION private.validate_member_connection()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NEW.connected_channel_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM public.channels AS channel
        WHERE channel.channel_id = NEW.connected_channel_id
          AND channel.event_id = NEW.event_id
          AND channel.channel_type <> 'GLOBAL'
    ) THEN
        RAISE EXCEPTION 'Connected channel must be an operational channel in the event'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION private.validate_member_connection() FROM PUBLIC;

CREATE TRIGGER validate_member_connection
BEFORE INSERT OR UPDATE OF event_id, connected_channel_id ON public.event_members
FOR EACH ROW EXECUTE FUNCTION private.validate_member_connection();
