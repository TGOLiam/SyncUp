-- Join codes and request hashes are backend-only; they are not exposed as tables.
CREATE TABLE private.event_join_codes (
    event_id uuid PRIMARY KEY REFERENCES public.events(event_id) ON DELETE CASCADE,
    code text NOT NULL UNIQUE CHECK (code ~ '^[0-9]{6}$')
);

CREATE TABLE private.event_creation_requests (
    event_id uuid PRIMARY KEY REFERENCES public.events(event_id) ON DELETE CASCADE,
    request_hash bytea NOT NULL
);

REVOKE ALL ON private.event_join_codes, private.event_creation_requests
    FROM PUBLIC, anon, authenticated;

-- A polygon is an ordered array of at least three normalized {x, y} vertices.
CREATE FUNCTION private.is_normalized_polygon(vertices jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
SET search_path = ''
AS $$
DECLARE
    point jsonb;
BEGIN
    IF jsonb_typeof(vertices) IS DISTINCT FROM 'array' OR jsonb_array_length(vertices) < 3 THEN
        RETURN false;
    END IF;
    FOR point IN SELECT value FROM jsonb_array_elements(vertices)
    LOOP
        IF jsonb_typeof(point) IS DISTINCT FROM 'object'
            OR jsonb_typeof(point -> 'x') IS DISTINCT FROM 'number'
            OR jsonb_typeof(point -> 'y') IS DISTINCT FROM 'number' THEN
            RETURN false;
        END IF;
        IF (point ->> 'x')::numeric NOT BETWEEN 0 AND 1
            OR (point ->> 'y')::numeric NOT BETWEEN 0 AND 1 THEN
            RETURN false;
        END IF;
    END LOOP;
    RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION private.is_normalized_polygon(jsonb) FROM PUBLIC;

-- One request creates the event, creator membership, Global channel, and setup.
-- The same event ID and payload can be retried safely after an uncertain response.
CREATE FUNCTION public.create_event(
    p_event_id uuid,
    p_name text,
    p_venue text,
    p_start_at timestamptz,
    p_end_at timestamptz,
    p_layout jsonb,
    p_zones jsonb,
    p_phases jsonb,
    p_channels jsonb
)
RETURNS TABLE (event_id uuid, join_code text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor uuid := auth.uid();
    request_hash bytea;
    existing_creator uuid;
    existing_hash bytea;
    item jsonb;
    candidate text;
    random_bytes bytea;
    attempt integer;
    created_code text;
BEGIN
    IF actor IS NULL OR NOT EXISTS (SELECT 1 FROM public.users WHERE user_id = actor) THEN
        RAISE EXCEPTION 'Authentication and a user profile are required' USING ERRCODE = '42501';
    END IF;
    IF p_event_id IS NULL OR p_name IS NULL OR length(trim(p_name)) = 0
        OR p_venue IS NULL OR length(trim(p_venue)) = 0
        OR p_start_at IS NULL OR p_end_at IS NULL OR p_end_at <= p_start_at THEN
        RAISE EXCEPTION 'Invalid event details' USING ERRCODE = '22023';
    END IF;
    IF jsonb_typeof(p_zones) IS DISTINCT FROM 'array'
        OR jsonb_typeof(p_phases) IS DISTINCT FROM 'array'
        OR jsonb_typeof(p_channels) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'Zones, phases, and channels must be arrays' USING ERRCODE = '22023';
    END IF;
    IF jsonb_array_length(p_phases) = 0 THEN
        RAISE EXCEPTION 'At least one phase is required' USING ERRCODE = '22023';
    END IF;
    IF p_layout IS NOT NULL AND p_layout <> 'null'::jsonb THEN
        IF jsonb_typeof(p_layout) IS DISTINCT FROM 'object'
            OR jsonb_typeof(p_layout -> 'aspect_ratio') IS DISTINCT FROM 'number'
            OR NOT private.is_normalized_polygon(p_layout -> 'boundary_vertices') THEN
            RAISE EXCEPTION 'Invalid venue layout' USING ERRCODE = '22023';
        END IF;
        IF (p_layout ->> 'aspect_ratio')::double precision <= 0 THEN
            RAISE EXCEPTION 'Invalid venue aspect ratio' USING ERRCODE = '22023';
        END IF;
    ELSIF jsonb_array_length(p_zones) > 0 THEN
        RAISE EXCEPTION 'Zones require a venue layout' USING ERRCODE = '22023';
    END IF;

    request_hash := extensions.digest(
        jsonb_build_array(p_event_id, p_name, p_venue, p_start_at, p_end_at,
            p_layout, p_zones, p_phases, p_channels)::text, 'sha256'
    );
    -- Serialize retries for the same ID, including concurrent requests.
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_event_id::text, 0));
    SELECT e.creator_id, r.request_hash INTO existing_creator, existing_hash
    FROM public.events AS e
    LEFT JOIN private.event_creation_requests AS r ON r.event_id = e.event_id
    WHERE e.event_id = p_event_id;
    IF FOUND THEN
        IF existing_creator <> actor OR existing_hash IS DISTINCT FROM request_hash THEN
            RAISE EXCEPTION 'Event ID already in use' USING ERRCODE = '23505';
        END IF;
        RETURN QUERY SELECT c.event_id, c.code FROM private.event_join_codes AS c
            WHERE c.event_id = p_event_id;
        RETURN;
    END IF;

    INSERT INTO public.events (event_id, creator_id, name, venue, start_at, end_at)
    VALUES (p_event_id, actor, trim(p_name), trim(p_venue), p_start_at, p_end_at);
    INSERT INTO public.event_members (member_id, event_id, user_id)
    VALUES (pg_catalog.gen_random_uuid(), p_event_id, actor);
    INSERT INTO public.channels (channel_id, event_id, name, channel_type)
    VALUES (pg_catalog.gen_random_uuid(), p_event_id, 'Global', 'GLOBAL');

    IF p_layout IS NOT NULL AND p_layout <> 'null'::jsonb THEN
        INSERT INTO public.venue_layouts (event_id, aspect_ratio, boundary_vertices)
        VALUES (p_event_id, (p_layout ->> 'aspect_ratio')::double precision, p_layout -> 'boundary_vertices');
    END IF;
    FOR item IN SELECT value FROM jsonb_array_elements(p_zones)
    LOOP
        IF NOT private.is_normalized_polygon(item -> 'vertices') THEN
            RAISE EXCEPTION 'Invalid zone vertices' USING ERRCODE = '22023';
        END IF;
        INSERT INTO public.zones (zone_id, event_id, name, vertices)
        VALUES ((item ->> 'zone_id')::uuid, p_event_id, item ->> 'name', item -> 'vertices');
    END LOOP;
    FOR item IN SELECT value FROM jsonb_array_elements(p_phases)
    LOOP
        INSERT INTO public.phases (phase_id, event_id, name, sequence_number, duration)
        VALUES ((item ->> 'phase_id')::uuid, p_event_id, item ->> 'name',
            (item ->> 'sequence_number')::integer, item ->> 'duration');
    END LOOP;
    FOR item IN SELECT value FROM jsonb_array_elements(p_channels)
    LOOP
        IF item ->> 'channel_type' NOT IN ('CUSTOM', 'ZONE') OR item ->> 'channel_type' IS NULL THEN
            RAISE EXCEPTION 'Invalid operational channel type' USING ERRCODE = '22023';
        END IF;
        INSERT INTO public.channels (channel_id, event_id, name, channel_type, zone_id)
        VALUES ((item ->> 'channel_id')::uuid, p_event_id, item ->> 'name',
            item ->> 'channel_type', NULLIF(item ->> 'zone_id', '')::uuid);
    END LOOP;

    FOR attempt IN 1..100 LOOP
        random_bytes := extensions.gen_random_bytes(4);
        candidate := pg_catalog.lpad((
            (get_byte(random_bytes, 0)::bigint * 16777216
            + get_byte(random_bytes, 1)::bigint * 65536
            + get_byte(random_bytes, 2)::bigint * 256
            + get_byte(random_bytes, 3)::bigint) % 1000000
        )::text, 6, '0');
        INSERT INTO private.event_join_codes (event_id, code)
        VALUES (p_event_id, candidate)
        ON CONFLICT (code) DO NOTHING
        RETURNING code INTO created_code;
        EXIT WHEN created_code IS NOT NULL;
    END LOOP;
    IF created_code IS NULL THEN
        RAISE EXCEPTION 'Could not allocate an event code' USING ERRCODE = '54000';
    END IF;
    INSERT INTO private.event_creation_requests (event_id, request_hash)
    VALUES (p_event_id, request_hash);
    RETURN QUERY SELECT p_event_id, created_code;
END;
$$;

REVOKE ALL ON FUNCTION public.create_event(uuid, text, text, timestamptz, timestamptz, jsonb, jsonb, jsonb, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_event(uuid, text, text, timestamptz, timestamptz, jsonb, jsonb, jsonb, jsonb) TO authenticated;
