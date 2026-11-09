-- Additive upgrade: never edit/reapply the original guest-table script to change existing databases.
BEGIN;
ALTER TABLE public.guest_apply_sessions
    ADD COLUMN IF NOT EXISTS apply_count integer NOT NULL DEFAULT 0 CHECK (apply_count >= 0);
-- Old history recorded distinct grants only, so conservatively seed at least that count without resetting newer clicks.
UPDATE public.guest_apply_sessions AS session
SET apply_count = GREATEST(session.apply_count,
    (SELECT count(*)::integer FROM public.guest_campaign_access AS access WHERE access.guest_hash = session.guest_hash));
COMMIT;
