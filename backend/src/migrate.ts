import { readFile } from "node:fs/promises";
import pg from "pg";

const sql = await readFile(new URL("../sql/schema.sql", import.meta.url), "utf8");
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });

try {
  await pool.query(sql);
  console.log("Database schema initialized");
} finally {
  await pool.end();
}
