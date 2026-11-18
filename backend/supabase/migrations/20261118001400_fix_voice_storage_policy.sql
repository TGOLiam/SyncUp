-- Qualify the storage object name; channels also have a name column.
DROP POLICY members_read_voice_objects ON storage.objects;
CREATE POLICY members_read_voice_objects ON storage.objects
FOR SELECT TO authenticated
USING (
    bucket_id = 'voice-messages'
    AND EXISTS (
        SELECT 1 FROM public.messages AS message
        JOIN public.channels AS channel ON channel.channel_id = message.channel_id
        WHERE message.message_type = 'VOICE'
          AND message.audio_reference = storage.objects.name
          AND channel.event_id IN (SELECT private.my_event_ids())
    )
);
