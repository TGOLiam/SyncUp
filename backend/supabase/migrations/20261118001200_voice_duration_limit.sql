-- Metadata cannot claim a voice message longer than the product limit.
-- Upload validation must also inspect the audio before voice writes are enabled.
ALTER TABLE public.messages
ADD CONSTRAINT voice_duration_limit CHECK (
    message_type <> 'VOICE' OR audio_duration_ms <= 10000
);
