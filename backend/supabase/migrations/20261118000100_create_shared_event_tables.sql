-- Shared event data. Local drafts and preview records remain on the device.
CREATE TABLE public.users (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    email text NOT NULL
);

CREATE TABLE public.events (
    event_id uuid PRIMARY KEY,
    creator_id uuid NOT NULL REFERENCES public.users(user_id),
    name text NOT NULL CHECK (length(trim(name)) > 0),
    venue text NOT NULL CHECK (length(trim(venue)) > 0),
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL CHECK (end_at > start_at),
    current_phase_id uuid
);

CREATE TABLE public.venue_layouts (
    event_id uuid PRIMARY KEY REFERENCES public.events(event_id) ON DELETE CASCADE,
    aspect_ratio double precision NOT NULL CHECK (aspect_ratio > 0),
    boundary_vertices jsonb NOT NULL CHECK (jsonb_typeof(boundary_vertices) = 'array')
);

CREATE TABLE public.zones (
    zone_id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES public.events(event_id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) > 0),
    vertices jsonb NOT NULL CHECK (jsonb_typeof(vertices) = 'array'),
    UNIQUE (event_id, zone_id)
);

CREATE TABLE public.phases (
    phase_id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES public.events(event_id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) > 0),
    sequence_number integer NOT NULL CHECK (sequence_number > 0),
    duration text NOT NULL CHECK (
        duration ~ '^[0-9]{2}:[0-5][0-9]$' AND duration <> '00:00'
    ),
    status text NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'ACTIVE', 'COMPLETED')),
    UNIQUE (event_id, sequence_number),
    UNIQUE (event_id, phase_id)
);

-- The event can only reference a phase belonging to that same event.
ALTER TABLE public.events
    ADD CONSTRAINT event_current_phase_fk
    FOREIGN KEY (event_id, current_phase_id)
    REFERENCES public.phases(event_id, phase_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE public.channels (
    channel_id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES public.events(event_id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) > 0),
    channel_type text NOT NULL CHECK (channel_type IN ('GLOBAL', 'ZONE', 'CUSTOM')),
    zone_id uuid,
    UNIQUE (event_id, channel_id),
    FOREIGN KEY (event_id, zone_id) REFERENCES public.zones(event_id, zone_id)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK (channel_type <> 'ZONE' OR zone_id IS NOT NULL)
);

CREATE TABLE public.event_members (
    member_id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES public.events(event_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    current_zone_id uuid,
    location_reported_at timestamptz,
    connected_channel_id uuid,
    UNIQUE (event_id, user_id),
    UNIQUE (event_id, member_id),
    FOREIGN KEY (event_id, current_zone_id) REFERENCES public.zones(event_id, zone_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (event_id, connected_channel_id) REFERENCES public.channels(event_id, channel_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX zones_by_event ON public.zones(event_id);
CREATE INDEX channels_by_event ON public.channels(event_id);
CREATE INDEX members_by_user ON public.event_members(user_id);
CREATE UNIQUE INDEX one_global_channel ON public.channels(event_id)
    WHERE channel_type = 'GLOBAL';
CREATE UNIQUE INDEX one_active_phase ON public.phases(event_id)
    WHERE status = 'ACTIVE';

-- Deny client access until the event-membership policies are implemented.
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.venue_layouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.zones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.event_members ENABLE ROW LEVEL SECURITY;
