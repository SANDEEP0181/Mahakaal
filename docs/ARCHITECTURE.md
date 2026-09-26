# Mahakaal Architecture

The Android client is untrusted. It requests mining actions and displays server results.

The API is the source of truth for:
- mining session state
- reward calculation
- KAAL balance
- referral accounting
- withdrawal status

PostgreSQL stores durable records. Redis handles temporary state and rate limiting. Blockchain integration is isolated behind a service boundary and will be added after the core ledger is stable.
