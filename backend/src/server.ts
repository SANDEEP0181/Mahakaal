import crypto from "node:crypto";
import Fastify from "fastify";
import cors from "@fastify/cors";
import { pool, initRedis, redis } from "./db.js";
import { hashPassword, verifyPassword } from "./password.js";
import { issueToken, verifyToken } from "./auth.js";
import { getBlockchainProvider } from "./blockchain.js";

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

async function auth(req: any, reply: any) {
  const h = req.headers.authorization ?? "";
  const u = h.startsWith("Bearer ") ? verifyToken(h.slice(7)) : null;
  if (!u) return reply.code(401).send({ error: "Unauthorized" });
  const r = await pool.query("SELECT id,username,status,role FROM users WHERE id=$1", [u.id]);
  const dbUser = r.rows[0];
  if (!dbUser || dbUser.status !== "active") return reply.code(403).send({ error: "Account is not active" });
  req.user = dbUser;
}

async function admin(req: any, reply: any) {
  await auth(req, reply);
  if (reply.sent) return;
  if (req.user.role !== "admin") return reply.code(403).send({ error: "Admin access required" });
}

async function configNumber(key: string, fallback: number) {
  const r = await pool.query("SELECT value FROM app_config WHERE key=$1", [key]);
  const n = Number(r.rows[0]?.value);
  return Number.isFinite(n) ? n : fallback;
}

async function reward(start: Date, now = new Date()) {
  const rate = await configNumber("KAAL_RATE_PER_HOUR", 1);
  const maxHours = await configNumber("MAX_SESSION_HOURS", 24);
  const sec = Math.max(0, (now.getTime() - start.getTime()) / 1000);
  return { amount: Math.min(sec, maxHours * 3600) / 3600 * rate, rate, maxHours };
}

async function throttle(key: string, limit: number, windowSeconds: number) {
  const bucket = "rl:" + key;
  const count = await redis.incr(bucket);
  if (count === 1) await redis.expire(bucket, windowSeconds);
  return count <= limit;
}

async function audit(actorId: string, action: string, metadata: any = {}) {
  await pool.query("INSERT INTO admin_audit_logs(actor_id,action,metadata) VALUES($1,$2,$3)", [actorId, action, JSON.stringify(metadata)]);
}

app.get("/health", async () => ({ ok: true, service: "mahakaal-api", token: "KAAL" }));

app.post("/api/v1/auth/register", async (req: any, reply: any) => {
  const b = req.body ?? {};
  if (!b.username || !b.password || String(b.password).length < 8) return reply.code(400).send({ error: "Username and password (8+ chars) are required" });
  if (!(await throttle("register:" + req.ip, 5, 3600))) return reply.code(429).send({ error: "Too many registration attempts" });
  const username = String(b.username).trim().toLowerCase();
  const referralCode = b.referralCode ? String(b.referralCode).trim().toUpperCase() : null;
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    let referrerId: string | null = null;
    if (referralCode) {
      const rr = await c.query("SELECT id FROM users WHERE referral_code=$1", [referralCode]);
      if (!rr.rowCount) { await c.query("ROLLBACK"); return reply.code(400).send({ error: "Invalid referral code" }); }
      referrerId = rr.rows[0].id;
    }
    const code = "K" + crypto.randomUUID().replaceAll("-", "").slice(0, 10).toUpperCase();
    const r = await c.query("INSERT INTO users(username,password_hash,referral_code,referred_by) VALUES($1,$2,$3,$4) RETURNING id,username,role", [username, hashPassword(b.password), code, referrerId]);
    const u = r.rows[0];
    if (referrerId) await c.query("INSERT INTO referrals(referrer_id,referred_id) VALUES($1,$2)", [referrerId, u.id]);
    await c.query("COMMIT");
    return { token: issueToken(u), user: u, referralCode: code };
  } catch {
    await c.query("ROLLBACK");
    return reply.code(409).send({ error: "Username already exists" });
  } finally { c.release(); }
});

app.post("/api/v1/auth/login", async (req: any, reply: any) => {
  const b = req.body ?? {};
  if (!(await throttle("login:" + req.ip, 20, 900))) return reply.code(429).send({ error: "Too many login attempts" });
  const r = await pool.query("SELECT id,username,password_hash,status,role FROM users WHERE username=$1", [String(b.username ?? "").trim().toLowerCase()]);
  const u = r.rows[0];
  if (!u || u.status !== "active" || !verifyPassword(b.password ?? "", u.password_hash)) return reply.code(401).send({ error: "Invalid login" });
  return { token: issueToken({ id: u.id, username: u.username }), user: { id: u.id, username: u.username, role: u.role } };
});

app.get("/api/v1/me", { preHandler: auth }, async (req: any) => {
  const r = await pool.query("SELECT id,username,status,role,referral_code,created_at FROM users WHERE id=$1", [req.user.id]);
  return r.rows[0];
});

app.get("/api/v1/mining/status", { preHandler: auth }, async (req: any) => {
  const s = await pool.query("SELECT id,started_at FROM mining_sessions WHERE user_id=$1 AND status='active' LIMIT 1", [req.user.id]);
  const b = await pool.query("SELECT COALESCE(SUM(amount),0) balance FROM reward_ledger WHERE user_id=$1 AND asset='KAAL'", [req.user.id]);
  const w = await pool.query("SELECT COALESCE(SUM(amount),0) pending FROM withdrawals WHERE user_id=$1 AND status IN ('pending','approved')", [req.user.id]);
  const cfg = await reward(new Date());
  const x = s.rows[0];
  return { mining: !!x, sessionId: x?.id ?? null, startedAt: x?.started_at ?? null, balance: b.rows[0].balance, reserved: w.rows[0].pending, available: (Number(b.rows[0].balance) - Number(w.rows[0].pending)).toFixed(12), ratePerHour: cfg.rate, maxSessionHours: cfg.maxHours, token: "KAAL" };
});

app.post("/api/v1/mining/start", { preHandler: auth }, async (req: any, reply: any) => {
  if (!(await throttle("mine-start:" + req.user.id, 10, 3600))) return reply.code(429).send({ error: "Too many mining start requests" });
  const e = await pool.query("SELECT id FROM mining_sessions WHERE user_id=$1 AND status='active' LIMIT 1", [req.user.id]);
  if (e.rowCount) return reply.code(409).send({ error: "Mining session already active", sessionId: e.rows[0].id });
  const r = await pool.query("INSERT INTO mining_sessions(user_id,started_at,status) VALUES($1,now(),'active') RETURNING id,started_at", [req.user.id]);
  return { mining: true, sessionId: r.rows[0].id, startedAt: r.rows[0].started_at };
});

app.post("/api/v1/mining/claim", { preHandler: auth }, async (req: any, reply: any) => {
  if (!(await throttle("mine-claim:" + req.user.id, 20, 3600))) return reply.code(429).send({ error: "Too many claim requests" });
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    const s = await c.query("SELECT id,started_at FROM mining_sessions WHERE user_id=$1 AND status='active' FOR UPDATE", [req.user.id]);
    if (!s.rowCount) { await c.query("ROLLBACK"); return reply.code(409).send({ error: "No active mining session" }); }
    const x = s.rows[0];
    const cfg = await reward(new Date(x.started_at));
    if (cfg.amount <= 0) { await c.query("ROLLBACK"); return { claimed: "0" }; }
    await c.query("INSERT INTO reward_ledger(user_id,session_id,amount,asset,reason) VALUES($1,$2,$3,'KAAL','mining_claim')", [req.user.id, x.id, cfg.amount.toFixed(12)]);
    await c.query("UPDATE mining_sessions SET status='claimed',ended_at=now() WHERE id=$1", [x.id]);
    await c.query("COMMIT");
    return { claimed: cfg.amount.toFixed(12), token: "KAAL", sessionId: x.id };
  } catch (e) { await c.query("ROLLBACK"); throw e; } finally { c.release(); }
});

app.get("/api/v1/rewards", { preHandler: auth }, async (req: any) => {
  const r = await pool.query("SELECT id,amount,asset,reason,created_at FROM reward_ledger WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100", [req.user.id]);
  return { items: r.rows };
});

app.get("/api/v1/wallet", { preHandler: auth }, async (req: any) => {
  const r = await pool.query("SELECT id,address,network,created_at FROM wallets WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1", [req.user.id]);
  return r.rows[0] ?? null;
});

app.post("/api/v1/wallet", { preHandler: auth }, async (req: any, reply: any) => {
  const b = req.body ?? {};
  if (!b.address || !b.network) return reply.code(400).send({ error: "Wallet address and network are required" });
  const r = await pool.query("INSERT INTO wallets(user_id,address,network) VALUES($1,$2,$3) RETURNING id,address,network,created_at", [req.user.id, String(b.address).trim(), String(b.network).trim()]);
  return r.rows[0];
});

app.get("/api/v1/referral", { preHandler: auth }, async (req: any) => {
  const u = await pool.query("SELECT referral_code FROM users WHERE id=$1", [req.user.id]);
  const r = await pool.query("SELECT COUNT(*)::int count FROM referrals WHERE referrer_id=$1", [req.user.id]);
  return { referralCode: u.rows[0]?.referral_code ?? null, referrals: r.rows[0].count };
});

app.post("/api/v1/referral/apply", { preHandler: auth }, async (req: any, reply: any) => {
  const code = String(req.body?.referralCode ?? "").trim().toUpperCase();
  if (!code) return reply.code(400).send({ error: "Referral code is required" });
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    const me = await c.query("SELECT referred_by FROM users WHERE id=$1 FOR UPDATE", [req.user.id]);
    if (me.rows[0]?.referred_by) { await c.query("ROLLBACK"); return reply.code(409).send({ error: "Referral already applied" }); }
    const ref = await c.query("SELECT id FROM users WHERE referral_code=$1", [code]);
    if (!ref.rowCount || ref.rows[0].id === req.user.id) { await c.query("ROLLBACK"); return reply.code(400).send({ error: "Invalid referral code" }); }
    await c.query("UPDATE users SET referred_by=$1 WHERE id=$2", [ref.rows[0].id, req.user.id]);
    await c.query("INSERT INTO referrals(referrer_id,referred_id) VALUES($1,$2) ON CONFLICT DO NOTHING", [ref.rows[0].id, req.user.id]);
    await c.query("COMMIT");
    return { ok: true };
  } catch (e) { await c.query("ROLLBACK"); throw e; } finally { c.release(); }
});

app.get("/api/v1/withdrawals", { preHandler: auth }, async (req: any) => {
  const r = await pool.query("SELECT id,amount,address,network,status,created_at FROM withdrawals WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100", [req.user.id]);
  return { items: r.rows };
});

app.post("/api/v1/withdrawals", { preHandler: auth }, async (req: any, reply: any) => {
  const amount = Number(req.body?.amount);
  const address = String(req.body?.address ?? "").trim();
  const network = String(req.body?.network ?? process.env.KAAL_NETWORK ?? "kaal-testnet").trim();
  const idempotencyKey = String(req.headers["idempotency-key"] ?? req.body?.idempotencyKey ?? "").trim() || null;
  const minWithdrawal = await configNumber("MIN_WITHDRAWAL", 10);
  const payoutPaused = (await pool.query("SELECT value FROM app_config WHERE key='PAYOUT_PAUSED'")).rows[0]?.value === "true";
  if (payoutPaused) return reply.code(503).send({ error: "Payouts are temporarily paused" });
  if (!Number.isFinite(amount) || amount <= 0 || !address) return reply.code(400).send({ error: "Positive amount and wallet address are required" });
  const maxWithdrawal = await configNumber("MAX_WITHDRAWAL", 1000);
  const dailyWithdrawalLimit = await configNumber("DAILY_WITHDRAWAL_LIMIT", 5000);
  if (amount < minWithdrawal) return reply.code(400).send({ error: `Minimum withdrawal is ${minWithdrawal} KAAL` });
  if (amount > maxWithdrawal) return reply.code(400).send({ error: `Maximum withdrawal is ${maxWithdrawal} KAAL` });
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    await c.query("SELECT pg_advisory_xact_lock(hashtext($1))", [req.user.id]);
    if (idempotencyKey) {
      const existing = await c.query("SELECT id,amount,address,network,status,created_at FROM withdrawals WHERE user_id=$1 AND idempotency_key=$2", [req.user.id,idempotencyKey]);
      if (existing.rowCount) { await c.query("COMMIT"); return existing.rows[0]; }
    }
    const bal = await c.query("SELECT COALESCE(SUM(amount),0) balance FROM reward_ledger WHERE user_id=$1 AND asset='KAAL'", [req.user.id]);
    const reserved = await c.query("SELECT COALESCE(SUM(amount),0) amount FROM withdrawals WHERE user_id=$1 AND status IN ('pending','approved')", [req.user.id]);
    const daily = await c.query("SELECT COALESCE(SUM(amount),0) amount FROM withdrawals WHERE user_id=$1 AND created_at >= now() - interval '24 hours' AND status NOT IN ('rejected','failed')", [req.user.id]);
    if (Number(daily.rows[0].amount) + amount > dailyWithdrawalLimit) { await c.query("ROLLBACK"); return reply.code(400).send({ error: `24-hour withdrawal limit is ${dailyWithdrawalLimit} KAAL` }); }
    const available = Number(bal.rows[0].balance) - Number(reserved.rows[0].amount);
    if (amount > available) { await c.query("ROLLBACK"); return reply.code(400).send({ error: "Insufficient available KAAL balance" }); }
    const r = await c.query("INSERT INTO withdrawals(user_id,amount,address,network,status,idempotency_key) VALUES($1,$2,$3,$4,'pending',$5) RETURNING id,amount,address,network,status,created_at", [req.user.id, amount.toFixed(12), address, network, idempotencyKey]);
    await c.query("COMMIT");
    return r.rows[0];
  } catch (e) { await c.query("ROLLBACK"); throw e; } finally { c.release(); }
});

app.get("/api/v1/admin/users", { preHandler: admin }, async (req: any) => {
  const r = await pool.query("SELECT id,username,status,role,referral_code,created_at FROM users ORDER BY created_at DESC LIMIT 500");
  return { items: r.rows };
});

app.post("/api/v1/admin/users/:id/freeze", { preHandler: admin }, async (req: any, reply: any) => {
  if (req.params.id === req.user.id) return reply.code(400).send({ error: "Admin cannot freeze self" });
  const r = await pool.query("UPDATE users SET status='frozen' WHERE id=$1 RETURNING id,username,status", [req.params.id]);
  if (!r.rowCount) return reply.code(404).send({ error: "User not found" });
  await audit(req.user.id, "freeze_user", { userId: req.params.id });
  return r.rows[0];
});

app.post("/api/v1/admin/users/:id/unfreeze", { preHandler: admin }, async (req: any, reply: any) => {
  const r = await pool.query("UPDATE users SET status='active' WHERE id=$1 RETURNING id,username,status", [req.params.id]);
  if (!r.rowCount) return reply.code(404).send({ error: "User not found" });
  await audit(req.user.id, "unfreeze_user", { userId: req.params.id });
  return r.rows[0];
});

app.get("/api/v1/admin/config", { preHandler: admin }, async () => {
  const r = await pool.query("SELECT key,value,updated_at FROM app_config ORDER BY key");
  return { items: r.rows };
});

app.post("/api/v1/admin/config", { preHandler: admin }, async (req: any, reply: any) => {
  const key = String(req.body?.key ?? "");
  const value = String(req.body?.value ?? "");
  if (!["KAAL_RATE_PER_HOUR","MAX_SESSION_HOURS","MIN_WITHDRAWAL"].includes(key) || !value || !Number.isFinite(Number(value)) || Number(value) < 0) return reply.code(400).send({ error: "Invalid config" });
  await pool.query("INSERT INTO app_config(key,value,updated_at) VALUES($1,$2,now()) ON CONFLICT(key) DO UPDATE SET value=EXCLUDED.value,updated_at=now()", [key, value]);
  await audit(req.user.id, "update_config", { key, value });
  return { key, value };
});

app.get("/api/v1/admin/withdrawals", { preHandler: admin }, async () => {
  const r = await pool.query("SELECT w.id,w.user_id,u.username,w.amount,w.address,w.status,w.created_at FROM withdrawals w JOIN users u ON u.id=w.user_id ORDER BY w.created_at DESC LIMIT 500");
  return { items: r.rows };
});

app.post("/api/v1/admin/withdrawals/:id/approve", { preHandler: admin }, async (req: any, reply: any) => {
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    const r = await c.query("UPDATE withdrawals SET status='approved' WHERE id=$1 AND status='pending' RETURNING id,user_id,amount,address,network,status", [req.params.id]);
    if (!r.rowCount) { await c.query("ROLLBACK"); return reply.code(409).send({ error: "Withdrawal not pending or not found" }); }
    const w = r.rows[0];
    await c.query("INSERT INTO blockchain_transactions(withdrawal_id,user_id,network,amount,status) VALUES($1,$2,$3,$4,'queued') ON CONFLICT(withdrawal_id) DO NOTHING", [w.id,w.user_id,w.network,w.amount]);
    await c.query("COMMIT");
    await audit(req.user.id, "approve_withdrawal", { withdrawalId: req.params.id });
    return { ...w, blockchainStatus: "queued", note: "Approved and queued. Blockchain broadcast remains disabled." };
  } catch (e) { await c.query("ROLLBACK"); throw e; } finally { c.release(); }
});

app.post("/api/v1/admin/withdrawals/:id/reject", { preHandler: admin }, async (req: any, reply: any) => {
  const r = await pool.query("UPDATE withdrawals SET status='rejected' WHERE id=$1 AND status='pending' RETURNING id,user_id,amount,address,status", [req.params.id]);
  if (!r.rowCount) return reply.code(409).send({ error: "Withdrawal not pending or not found" });
  await audit(req.user.id, "reject_withdrawal", { withdrawalId: req.params.id });
  return r.rows[0];
});

app.get("/api/v1/admin/audit", { preHandler: admin }, async () => {
  const r = await pool.query("SELECT id,actor_id,action,metadata,created_at FROM admin_audit_logs ORDER BY created_at DESC LIMIT 500");
  return { items: r.rows };
});

const port = Number(process.env.PORT ?? 3000);
await initRedis();
await app.listen({ port, host: "0.0.0.0" });
