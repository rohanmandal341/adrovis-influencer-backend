-- Run once on the core backend PRIMARY database before deploying the guest allowance.
-- This project has no migration runner; this file is not applied automatically.
BEGIN;
CREATE TABLE IF NOT EXISTS public.guest_apply_sessions (
    guest_hash varchar(64) PRIMARY KEY CHECK (guest_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.guest_campaign_access (
    guest_hash varchar(64) NOT NULL REFERENCES public.guest_apply_sessions(guest_hash) ON DELETE CASCADE,
    campaign_id uuid NOT NULL REFERENCES public.campaigns(id) ON DELETE CASCADE,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (guest_hash, campaign_id)
);
CREATE INDEX IF NOT EXISTS guest_campaign_access_campaign_idx ON public.guest_campaign_access(campaign_id);
ALTER TABLE public.guest_apply_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.guest_campaign_access ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM authenticated;
    END IF;
END $$;
COMMIT;
-- Run as the backend table owner. If the runtime DB role is separate, grant it backend-only
-- access with an explicit RLS policy; never grant browser roles access to these tables.
