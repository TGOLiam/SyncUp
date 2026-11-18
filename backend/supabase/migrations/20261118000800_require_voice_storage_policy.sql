-- Voice messages stay disabled until their storage access is protected.
CREATE POLICY voice_waits_for_storage ON public.messages
AS RESTRICTIVE
FOR INSERT TO authenticated
WITH CHECK (message_type = 'TEXT');
