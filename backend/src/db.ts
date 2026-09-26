import pg from "pg"; import {createClient} from "redis";
export const pool=new pg.Pool({connectionString:process.env.DATABASE_URL});
export const redis=createClient({url:process.env.REDIS_URL??"redis://localhost:6379"});
export async function initRedis(){if(!redis.isOpen)await redis.connect();}