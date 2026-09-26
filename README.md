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
