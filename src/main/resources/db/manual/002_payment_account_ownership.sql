-- Apply to the CORE primary database before deploying payment ownership enforcement.
-- Existing payment tables are owned by the payment module; no existing records are deleted or auto-linked.
BEGIN;
ALTER TABLE public.payment_transactions ADD COLUMN IF NOT EXISTS account_id uuid;
CREATE INDEX IF NOT EXISTS idx_payment_account_id ON public.payment_transactions(account_id);
-- NULL historical owners remain admin-readable; client verification requires a reviewed UUID link.
-- Required by the webhook's atomic ON CONFLICT retry claim. Existing duplicates need reviewed remediation.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_webhook_event_id ON public.payment_webhook_events(event_id);
-- Browser credentials must not bypass the API's financial authorization through direct table access.
ALTER TABLE public.payment_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_webhook_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM authenticated;
    END IF;
END $$;
-- Run as the table owner. A distinct backend role requires reviewed grants and a backend-only RLS policy.
COMMIT;
