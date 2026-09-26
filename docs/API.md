# Mahakaal API — Phase 4

Base URL: `http://localhost:3000`

## Public
- `GET /health`
- `POST /api/v1/auth/register` — body: `{username,password,referralCode?}`
- `POST /api/v1/auth/login` — body: `{username,password}`

## Authenticated user
Send `Authorization: Bearer <token>`.

- `GET /api/v1/me`
- `GET /api/v1/mining/status`
- `POST /api/v1/mining/start`
- `POST /api/v1/mining/claim`
- `GET /api/v1/rewards`
- `GET /api/v1/wallet`
- `POST /api/v1/wallet` — body: `{address,network}`
- `GET /api/v1/referral`
- `POST /api/v1/referral/apply` — body: `{referralCode}`
- `GET /api/v1/withdrawals`
- `POST /api/v1/withdrawals` — body: `{amount,address}`

## Admin
Admin users require `users.role='admin'`.

- `GET /api/v1/admin/users`
- `POST /api/v1/admin/users/:id/freeze`
- `POST /api/v1/admin/users/:id/unfreeze`
- `GET /api/v1/admin/config`
- `POST /api/v1/admin/config` — body: `{key,value}`
- `GET /api/v1/admin/withdrawals`
- `POST /api/v1/admin/withdrawals/:id/approve`
- `POST /api/v1/admin/withdrawals/:id/reject`
- `GET /api/v1/admin/audit`

Supported config keys:
- `KAAL_RATE_PER_HOUR`
- `MAX_SESSION_HOURS`

## Important
KAAL rewards are currently an internal server ledger. Admin approval does not send blockchain funds. Real token deployment and blockchain payout require a separate audited blockchain integration.
