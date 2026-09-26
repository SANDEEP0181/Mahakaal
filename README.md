# Mahakaal (KAAL)

Mahakaal is a server-authoritative mining and Web3 rewards platform.

## Stack
- Android: Kotlin + Jetpack Compose
- Backend: Node.js + TypeScript + Fastify
- Data: PostgreSQL + Redis
- Token: Mahakaal (KAAL)

## Architecture
Android App -> Mahakaal API -> PostgreSQL + Redis -> Mining Engine -> Reward Ledger -> Wallet/Withdrawal -> Blockchain

## Phase 1
Foundation, API health check, database schema, Redis, Android dashboard starter, and security-oriented server-authoritative design.

> Mining rewards are calculated and recorded by the server. The Android client is not a source of truth for balances.


## Phase 4
Phase 4 adds:
- Admin APIs with role checks and audit logs
- User freeze/unfreeze
- Database-backed KAAL rate and session configuration
- Wallet storage
- Referral codes and referral tracking
- Reward and withdrawal history
- Withdrawal request/review workflow
- Redis request throttling
- API and deployment documentation

Blockchain payouts are not enabled yet; KAAL remains an internal server ledger until a separate blockchain integration is implemented and verified.

## Phase 6
Phase 6 adds the Android admin entry point, admin API client methods, stronger development build configuration, and a blockchain transaction ledger foundation. The blockchain ledger stores withdrawal transaction state but does not broadcast transactions yet.
