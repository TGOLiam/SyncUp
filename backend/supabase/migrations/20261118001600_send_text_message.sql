-- Client-generated IDs make retries safe when a response is lost.
CREATE FUNCTION public.send_text_message(p_message_id uuid, p_channel_id uuid, p_text text)
RETURNS SETOF public.messages
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    sender uuid;
    existing public.messages%ROWTYPE;
BEGIN
    IF auth.uid() IS NULL OR p_message_id IS NULL OR p_channel_id IS NULL
       OR p_text IS NULL OR length(trim(p_text)) = 0 THEN
        RAISE EXCEPTION 'Invalid message' USING ERRCODE = '22023';
    END IF;

    SELECT member.member_id INTO sender
    FROM public.event_members AS member
    JOIN public.channels AS channel ON channel.event_id = member.event_id
    WHERE member.user_id = auth.uid() AND channel.channel_id = p_channel_id;
    IF sender IS NULL THEN
        RAISE EXCEPTION 'Channel not found or action not allowed' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, text_content)
    VALUES (p_message_id, p_channel_id, sender, 'TEXT', p_text)
    ON CONFLICT (message_id) DO NOTHING;

    SELECT * INTO existing FROM public.messages WHERE message_id = p_message_id;
    IF NOT FOUND OR existing.sender_member_id <> sender OR existing.channel_id <> p_channel_id
       OR existing.message_type <> 'TEXT' OR existing.text_content <> p_text THEN
        RAISE EXCEPTION 'Message ID already used' USING ERRCODE = '22023';
    END IF;
    RETURN NEXT existing;
END;
$$;

REVOKE ALL ON FUNCTION public.send_text_message(uuid, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.send_text_message(uuid, uuid, text) TO authenticated;
