import crypto from "node:crypto";
export type AuthUser = { id: string; username: string };
const secret = process.env.JWT_SECRET ?? "change-this-secret";
const b64 = (s:string)=>Buffer.from(s).toString("base64url");
export function issueToken(user:AuthUser){const now=Math.floor(Date.now()/1000);const h=b64(JSON.stringify({alg:"HS256",typ:"MK"}));const p=b64(JSON.stringify({...user,iat:now,exp:now+2592000}));const d=h+"."+p;const s=crypto.createHmac("sha256",secret).update(d).digest("base64url");return d+"."+s;}
export function verifyToken(t:string):AuthUser|null{try{const [h,p,s]=t.split(".");if(!h||!p||!s)return null;const e=crypto.createHmac("sha256",secret).update(h+"."+p).digest("base64url");if(s.length!==e.length||!crypto.timingSafeEqual(Buffer.from(s),Buffer.from(e)))return null;const x=JSON.parse(Buffer.from(p,"base64url").toString());return x.exp>Date.now()/1000&&x.id&&x.username?{id:x.id,username:x.username}:null;}catch{return null;}}