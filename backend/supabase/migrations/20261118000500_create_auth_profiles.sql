-- Auth owns credentials. This private trigger creates the app's public profile.
CREATE SCHEMA IF NOT EXISTS private;

CREATE FUNCTION private.create_user_profile()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    display_name text;
BEGIN
    IF jsonb_typeof(NEW.raw_user_meta_data -> 'name') IS DISTINCT FROM 'string' THEN
        RAISE EXCEPTION 'A display name is required' USING ERRCODE = '23514';
    END IF;

    display_name := trim(NEW.raw_user_meta_data ->> 'name');
    IF display_name = '' OR NEW.email IS NULL OR trim(NEW.email) = '' THEN
        RAISE EXCEPTION 'A display name and email are required' USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.users (user_id, name, email)
    VALUES (NEW.id, display_name, NEW.email);
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION private.create_user_profile() FROM PUBLIC;

CREATE TRIGGER syncup_create_user_profile
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION private.create_user_profile();

-- Keep the cached profile email aligned with confirmed Auth account changes.
CREATE FUNCTION private.sync_user_profile_email()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NEW.email IS NULL OR trim(NEW.email) = '' THEN
        RAISE EXCEPTION 'An email is required' USING ERRCODE = '23514';
    END IF;

    UPDATE public.users
    SET email = NEW.email
    WHERE user_id = NEW.id;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION private.sync_user_profile_email() FROM PUBLIC;

CREATE TRIGGER syncup_sync_user_profile_email
AFTER UPDATE OF email ON auth.users
FOR EACH ROW
WHEN (OLD.email IS DISTINCT FROM NEW.email)
EXECUTE FUNCTION private.sync_user_profile_email();
