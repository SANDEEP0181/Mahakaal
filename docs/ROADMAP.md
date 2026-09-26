# Mahakaal Roadmap

## Phase 1-6
Core Android client, server-authoritative mining ledger, authentication, PostgreSQL/Redis, referrals, wallet/withdrawal workflow, admin controls, audit logging, and blockchain transaction ledger foundation.

## Phase 7 — blockchain-ready foundation
- Server-side BlockchainProvider interface added.
- Disabled provider prevents accidental real payouts until a chain is selected and audited.
- Blockchain RPC/private-key environment placeholders added; secrets must never be committed.
- Android version bumped to 0.2.0.
- Android debug APK is configured as a GitHub Actions artifact.
- Signing files are ignored by Git.
- Real token broadcast remains disabled until the target blockchain and contract/token model are finalized.

## Phase 9 — token integration boundary
- Formal blockchain provider interface and server-side provider factory.
- EVM-style address validation boundary in the disabled provider.
- Chain ID, token contract address, and confirmation-count configuration placeholders.
- Authenticated blockchain configuration endpoint.
- Admin wallet-address validation endpoint with audit logging.
- Real token broadcast remains disabled until the KAAL chain and contract are finalized.
