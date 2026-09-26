# Admin Setup

After applying `backend/sql/schema.sql`, create an initial account through the normal register API.

Then promote that account to admin directly in PostgreSQL:

```sql
UPDATE users SET role='admin' WHERE username='your_admin_username';
```

Do not expose database credentials in the Android app or frontend.

## Admin controls
The Phase 4 API provides user freeze/unfreeze, mining-rate configuration, withdrawal review, and audit logs.

## Operational rule
Changing `KAAL_RATE_PER_HOUR` affects future reward calculations. Keep changes documented and review audit logs after every configuration change.

Blockchain withdrawals are intentionally not automated in this phase.
