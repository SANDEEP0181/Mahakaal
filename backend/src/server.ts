import Fastify from "fastify";
import cors from "@fastify/cors";
import {pool,initRedis} from "./db.js";
import {hashPassword,verifyPassword} from "./password.js";
import {issueToken,verifyToken} from "./auth.js";

const app=Fastify({logger:true});
await app.register(cors,{origin:true});
const RATE=Number(process.env.KAAL_RATE_PER_HOUR??"1");
const MAX_HOURS=Number(process.env.MAX_SESSION_HOURS??"24");

async function auth(req:any,reply:any){const h=req.headers.authorization??"";const u=h.startsWith("Bearer ")?verifyToken(h.slice(7)):null;if(!u)return reply.code(401).send({error:"Unauthorized"});req.user=u;}
function reward(start:Date,now=new Date()){const sec=Math.max(0,(now.getTime()-start.getTime())/1000);return Math.min(sec,MAX_HOURS*3600)/3600*RATE;}

app.get("/health",async()=>({ok:true,service:"mahakaal-api",token:"KAAL"}));

app.post("/api/v1/auth/register",async(req:any,reply:any)=>{const b=req.body??{};if(!b.username||!b.password||b.password.length<8)return reply.code(400).send({error:"Username and password (8+ chars) are required"});try{const r=await pool.query("INSERT INTO users(username,password_hash) VALUES($1,$2) RETURNING id,username",[String(b.username).trim().toLowerCase(),hashPassword(b.password)]);const u=r.rows[0];return{token:issueToken(u),user:u};}catch{return reply.code(409).send({error:"Username already exists"});}});

app.post("/api/v1/auth/login",async(req:any,reply:any)=>{const b=req.body??{};const r=await pool.query("SELECT id,username,password_hash,status FROM users WHERE username=$1",[String(b.username??"").trim().toLowerCase()]);const u=r.rows[0];if(!u||u.status!=="active"||!verifyPassword(b.password??"",u.password_hash))return reply.code(401).send({error:"Invalid login"});return{token:issueToken({id:u.id,username:u.username}),user:{id:u.id,username:u.username}};});

app.get("/api/v1/me",{preHandler:auth},async(req:any)=>{const r=await pool.query("SELECT id,username,status,created_at FROM users WHERE id=$1",[req.user.id]);return r.rows[0];});

app.get("/api/v1/mining/status",{preHandler:auth},async(req:any)=>{const s=await pool.query("SELECT id,started_at FROM mining_sessions WHERE user_id=$1 AND status='active' LIMIT 1",[req.user.id]);const b=await pool.query("SELECT COALESCE(SUM(amount),0) balance FROM reward_ledger WHERE user_id=$1 AND asset='KAAL'",[req.user.id]);const x=s.rows[0];return{mining:!!x,sessionId:x?.id??null,startedAt:x?.started_at??null,balance:b.rows[0].balance,ratePerHour:RATE,maxSessionHours:MAX_HOURS,token:"KAAL"};});

app.post("/api/v1/mining/start",{preHandler:auth},async(req:any,reply:any)=>{const e=await pool.query("SELECT id FROM mining_sessions WHERE user_id=$1 AND status='active' LIMIT 1",[req.user.id]);if(e.rowCount)return reply.code(409).send({error:"Mining session already active",sessionId:e.rows[0].id});const r=await pool.query("INSERT INTO mining_sessions(user_id,started_at,status) VALUES($1,now(),'active') RETURNING id,started_at",[req.user.id]);return{mining:true,sessionId:r.rows[0].id,startedAt:r.rows[0].started_at};});

app.post("/api/v1/mining/claim",{preHandler:auth},async(req:any,reply:any)=>{const c=await pool.connect();try{await c.query("BEGIN");const s=await c.query("SELECT id,started_at FROM mining_sessions WHERE user_id=$1 AND status='active' FOR UPDATE",[req.user.id]);if(!s.rowCount){await c.query("ROLLBACK");return reply.code(409).send({error:"No active mining session"});}const x=s.rows[0];const amount=reward(new Date(x.started_at));if(amount<=0){await c.query("ROLLBACK");return{claimed:"0"};}await c.query("INSERT INTO reward_ledger(user_id,session_id,amount,asset,reason) VALUES($1,$2,$3,'KAAL','mining_claim')",[req.user.id,x.id,amount.toFixed(12)]);await c.query("UPDATE mining_sessions SET status='claimed',ended_at=now() WHERE id=$1",[x.id]);await c.query("COMMIT");return{claimed:amount.toFixed(12),token:"KAAL",sessionId:x.id};}catch(e){await c.query("ROLLBACK");throw e;}finally{c.release();}});

const port=Number(process.env.PORT??3000);await initRedis();await app.listen({port,host:"0.0.0.0"});