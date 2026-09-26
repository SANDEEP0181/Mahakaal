# Deployment

## Local services

Use PostgreSQL and Redis. The example environment is in `.env.example`.

1. Copy `.env.example` to `.env`.
2. Set a long random `JWT_SECRET`.
3. Create the `mahakaal` PostgreSQL database.
4. Apply `backend/sql/schema.sql`.
5. Install backend dependencies with `npm install`.
6. Build with `npm run build`.
7. Start with `npm start`.

## Android

The current Android client uses `http://10.0.2.2:3000`, which is suitable for an Android emulator talking to a host-machine backend.

For a physical device or production deployment, use an HTTPS API URL and update the Android client accordingly.

## Production checklist

- HTTPS/TLS
- Strong database password
- Strong JWT secret
- Private PostgreSQL and Redis
- Database backups
- Request logging and monitoring
- Rate-limit review
- Admin account protection
- Blockchain payout integration only after security review
