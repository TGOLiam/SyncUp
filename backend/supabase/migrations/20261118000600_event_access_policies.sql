-- This helper reads membership without recursively invoking its own RLS policy.
-- The private schema is not exposed by the Data API.
CREATE FUNCTION private.my_event_ids()
RETURNS SETOF uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT event_id
    FROM public.event_members
    WHERE user_id = (SELECT auth.uid());
$$;

REVOKE ALL ON FUNCTION private.my_event_ids() FROM PUBLIC;
GRANT USAGE ON SCHEMA private TO authenticated;
GRANT EXECUTE ON FUNCTION private.my_event_ids() TO authenticated;

-- Only grant operations supported by these policies. Setup writes remain
-- unavailable until the event creation and joining functions are implemented.
REVOKE ALL ON public.users, public.events, public.venue_layouts, public.zones,
    public.phases, public.channels, public.event_members, public.messages
    FROM anon, authenticated;

GRANT SELECT ON public.users, public.events, public.venue_layouts, public.zones,
    public.phases, public.channels, public.event_members, public.messages
    TO authenticated;
GRANT UPDATE (current_zone_id, location_reported_at, connected_channel_id)
    ON public.event_members TO authenticated;
GRANT INSERT (message_id, channel_id, sender_member_id, message_type,
    text_content, audio_reference, audio_duration_ms)
    ON public.messages TO authenticated;

CREATE POLICY users_read_own_and_team ON public.users
FOR SELECT TO authenticated
USING (
    user_id = (SELECT auth.uid())
    OR EXISTS (
        SELECT 1
        FROM public.event_members AS member
        WHERE member.user_id = users.user_id
          AND member.event_id IN (SELECT private.my_event_ids())
    )
);

CREATE POLICY members_read_events ON public.events
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_read_layouts ON public.venue_layouts
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_read_zones ON public.zones
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_read_phases ON public.phases
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_read_channels ON public.channels
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_read_team ON public.event_members
FOR SELECT TO authenticated
USING (event_id IN (SELECT private.my_event_ids()));

CREATE POLICY members_update_own_presence ON public.event_members
FOR UPDATE TO authenticated
USING (user_id = (SELECT auth.uid()))
WITH CHECK (user_id = (SELECT auth.uid()));

CREATE POLICY members_read_messages ON public.messages
FOR SELECT TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.channels AS channel
        WHERE channel.channel_id = messages.channel_id
          AND channel.event_id IN (SELECT private.my_event_ids())
    )
);

CREATE POLICY members_send_messages ON public.messages
FOR INSERT TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.event_members AS member
        JOIN public.channels AS channel ON channel.event_id = member.event_id
        WHERE member.member_id = messages.sender_member_id
          AND member.user_id = (SELECT auth.uid())
          AND channel.channel_id = messages.channel_id
    )
);
