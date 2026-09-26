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

## Phase 12 — production hardening
- Android release version bumped to 0.3.0.
- Cleartext HTTP disabled in Android production manifest.
- Production deployment guidance updated for HTTPS and secret management.

## Phase 13 — Android/admin integration
- Android API client now reads blockchain configuration and admin transaction monitoring.
- Admin app shows blockchain transaction queue/status alongside withdrawals.
- Production release version remains 0.3.0.

## Phase 15 — production QA hardening
- Explicit withdrawal state-transition rules added.
- Admin approval/rejection now checks the current state transactionally.
- Withdrawal state-machine tests added to CI test suite.

## Phase 17 — Android safety and monitoring UI
- Added user withdrawal history to the Android dashboard.
- Added admin reconciliation visibility.
- Added emergency payout pause/resume controls to the Android admin panel.
- Added API client support for reconciliation and payout pause operations.
