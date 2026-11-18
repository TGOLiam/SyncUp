CREATE TABLE public.messages (
    message_id uuid PRIMARY KEY,
    channel_id uuid NOT NULL REFERENCES public.channels(channel_id) ON DELETE CASCADE,
    sender_member_id uuid NOT NULL REFERENCES public.event_members(member_id),
    message_type text NOT NULL CHECK (message_type IN ('TEXT', 'VOICE')),
    text_content text,
    audio_reference text,
    audio_duration_ms integer,
    sent_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (message_type = 'TEXT' AND text_content IS NOT NULL AND length(trim(text_content)) > 0
            AND audio_reference IS NULL AND audio_duration_ms IS NULL)
        OR
        (message_type = 'VOICE' AND text_content IS NULL
            AND audio_reference IS NOT NULL AND length(trim(audio_reference)) > 0
            AND audio_duration_ms IS NOT NULL AND audio_duration_ms > 0)
    )
);

CREATE INDEX messages_by_channel_time ON public.messages(channel_id, sent_at);

-- Policies are added separately; RLS denies direct client access until then.
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
