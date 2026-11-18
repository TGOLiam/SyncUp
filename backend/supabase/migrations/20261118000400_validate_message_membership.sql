-- A message sender and channel must belong to the same event.
CREATE FUNCTION public.validate_message_membership()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.channels AS channel
        JOIN public.event_members AS member ON member.event_id = channel.event_id
        WHERE channel.channel_id = NEW.channel_id
          AND member.member_id = NEW.sender_member_id
    ) THEN
        RAISE EXCEPTION 'Message sender and channel must belong to the same event'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.validate_message_membership() FROM PUBLIC;

CREATE TRIGGER validate_message_membership
BEFORE INSERT OR UPDATE OF channel_id, sender_member_id ON public.messages
FOR EACH ROW EXECUTE FUNCTION public.validate_message_membership();
