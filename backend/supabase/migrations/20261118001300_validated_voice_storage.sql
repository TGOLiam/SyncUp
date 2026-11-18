-- Only the trusted validation service writes; event members can read messages.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('voice-messages', 'voice-messages', false, 2097152, ARRAY['audio/mp4']);

CREATE POLICY members_read_voice_objects ON storage.objects
FOR SELECT TO authenticated
USING (
    bucket_id = 'voice-messages'
    AND EXISTS (
        SELECT 1 FROM public.messages AS message
        JOIN public.channels AS channel ON channel.channel_id = message.channel_id
        WHERE message.message_type = 'VOICE' AND message.audio_reference = name
          AND channel.event_id IN (SELECT private.my_event_ids())
    )
);

CREATE FUNCTION public.check_speech_lease(p_actor uuid, p_channel_id uuid, p_token uuid)
RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path = ''
AS $$
    SELECT EXISTS (
        SELECT 1 FROM private.channel_speech_leases AS lease
        JOIN public.channels AS channel ON channel.channel_id = lease.channel_id
        JOIN public.event_members AS member ON member.event_id = channel.event_id
            AND member.user_id = lease.user_id
        WHERE lease.channel_id = p_channel_id AND lease.user_id = p_actor
            AND lease.lease_token = p_token AND lease.expires_at > clock_timestamp()
    );
$$;

CREATE FUNCTION public.finish_validated_voice(
    p_actor uuid, p_channel_id uuid, p_token uuid, p_message_id uuid,
    p_audio_reference text, p_duration_ms integer
)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    lease private.channel_speech_leases%ROWTYPE;
    sender_id uuid;
BEGIN
    SELECT * INTO lease FROM private.channel_speech_leases
    WHERE channel_id = p_channel_id FOR UPDATE;
    IF NOT FOUND OR lease.user_id <> p_actor OR lease.lease_token <> p_token
        OR lease.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'Speech lease expired or invalid' USING ERRCODE = '42501';
    END IF;
    IF p_duration_ms NOT BETWEEN 1 AND 10000 OR p_audio_reference <>
        p_channel_id::text || '/' || p_message_id::text || '.m4a' THEN
        RAISE EXCEPTION 'Invalid voice message' USING ERRCODE = '22023';
    END IF;
    SELECT member.member_id INTO sender_id
    FROM public.event_members AS member
    JOIN public.channels AS channel ON channel.event_id = member.event_id
    WHERE channel.channel_id = p_channel_id AND member.user_id = p_actor;
    IF sender_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM storage.objects WHERE bucket_id = 'voice-messages' AND name = p_audio_reference
    ) THEN
        RAISE EXCEPTION 'Voice upload not available' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.messages (message_id, channel_id, sender_member_id, message_type, audio_reference, audio_duration_ms)
    VALUES (p_message_id, p_channel_id, sender_id, 'VOICE', p_audio_reference, p_duration_ms);
    DELETE FROM private.channel_speech_leases WHERE channel_id = p_channel_id AND lease_token = p_token;
END;
$$;

REVOKE ALL ON FUNCTION public.check_speech_lease(uuid, uuid, uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.finish_validated_voice(uuid, uuid, uuid, uuid, text, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_speech_lease(uuid, uuid, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.finish_validated_voice(uuid, uuid, uuid, uuid, text, integer) TO service_role;
