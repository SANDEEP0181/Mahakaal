# Mahakaal Security

## Current safety baseline
- JWT secret is mandatory at startup; the example placeholder is rejected.
- API body size is limited to 64 KiB.
- Restrictive CORS is used by default.
- Security and no-cache headers are applied to API responses.
- Login and registration are rate-limited through Redis.
- Mining start/claim requests are rate-limited.
- Withdrawals have configurable minimum, maximum, and 24-hour limits.
- Withdrawal idempotency keys are enforced inside a database transaction.
- PostgreSQL transaction advisory locking prevents concurrent withdrawal races for the same user.
- Payout emergency pause can block new withdrawals.
- Admin actions are authenticated, role-checked, and audited.
- Blockchain broadcast remains disabled until the final provider is deliberately configured.
- Private keys and signing credentials must remain outside GitHub.

- Admin reconciliation endpoint compares reward-ledger totals with withdrawal and blockchain status aggregates.
- Emergency payout pause can be enabled or disabled only by an authenticated admin and is audited.
- Operational indexes support safer/faster withdrawal, ledger, blockchain, and audit monitoring.
