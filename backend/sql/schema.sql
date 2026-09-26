CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS users(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),username TEXT UNIQUE NOT NULL,password_hash TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','frozen','banned')),created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS mining_sessions(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),user_id UUID NOT NULL REFERENCES users(id),started_at TIMESTAMPTZ NOT NULL DEFAULT now(),ended_at TIMESTAMPTZ,status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','claimed','cancelled')));
CREATE UNIQUE INDEX IF NOT EXISTS one_active_session_per_user ON mining_sessions(user_id) WHERE status='active';
CREATE TABLE IF NOT EXISTS reward_ledger(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),user_id UUID NOT NULL REFERENCES users(id),session_id UUID REFERENCES mining_sessions(id),amount NUMERIC(30,12) NOT NULL,asset TEXT NOT NULL DEFAULT 'KAAL',reason TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE UNIQUE INDEX IF NOT EXISTS one_claim_per_session ON reward_ledger(session_id,reason) WHERE reason='mining_claim';
CREATE TABLE IF NOT EXISTS wallets(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),user_id UUID NOT NULL REFERENCES users(id),address TEXT NOT NULL,network TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS referrals(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),referrer_id UUID NOT NULL REFERENCES users(id),referred_id UUID NOT NULL REFERENCES users(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(referrer_id,referred_id));
CREATE TABLE IF NOT EXISTS withdrawals(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),user_id UUID NOT NULL REFERENCES users(id),amount NUMERIC(30,12) NOT NULL,address TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'pending',created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS admin_audit_logs(id UUID PRIMARY KEY DEFAULT gen_random_uuid(),action TEXT NOT NULL,actor_id UUID,metadata JSONB NOT NULL DEFAULT '{}',created_at TIMESTAMPTZ NOT NULL DEFAULT now());

ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('user','admin'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code TEXT UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS referred_by UUID REFERENCES users(id);
CREATE TABLE IF NOT EXISTS app_config(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
INSERT INTO app_config(key,value) VALUES('KAAL_RATE_PER_HOUR','1'),('MAX_SESSION_HOURS','24') ON CONFLICT(key) DO NOTHING;

CREATE TABLE IF NOT EXISTS blockchain_transactions(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 withdrawal_id UUID UNIQUE REFERENCES withdrawals(id),
 user_id UUID NOT NULL REFERENCES users(id),
 network TEXT NOT NULL,
 asset TEXT NOT NULL DEFAULT 'KAAL',
 amount NUMERIC(30,12) NOT NULL,
 tx_hash TEXT UNIQUE,
 status TEXT NOT NULL DEFAULT 'queued' CHECK(status IN ('queued','submitted','confirmed','failed')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE withdrawals ADD COLUMN IF NOT EXISTS network TEXT NOT NULL DEFAULT 'kaal-testnet';
ALTER TABLE withdrawals ADD COLUMN IF NOT EXISTS admin_note TEXT;
ALTER TABLE withdrawals ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;
ALTER TABLE withdrawals ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS withdrawal_idempotency_user ON withdrawals(user_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
INSERT INTO app_config(key,value) VALUES('MIN_WITHDRAWAL','10') ON CONFLICT(key) DO NOTHING;


-- Phase 10: idempotent withdrawal debit protection
CREATE UNIQUE INDEX IF NOT EXISTS reward_ledger_withdrawal_debit_unique
ON reward_ledger(user_id, reason)
WHERE reason LIKE 'withdrawal_debit:%';

INSERT INTO app_config(key,value) VALUES('PAYOUT_PAUSED','false') ON CONFLICT(key) DO NOTHING;
