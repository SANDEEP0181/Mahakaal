import Fastify from "fastify";
import cors from "@fastify/cors";

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

app.get("/health", async () => ({
  ok: true,
  service: "mahakaal-api",
  token: "KAAL"
}));

app.get("/api/v1/mining/status", async () => ({
  mining: false,
  balance: "0",
  token: "KAAL",
  message: "Mining engine foundation ready"
}));

const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: "0.0.0.0" }).catch((error) => {
  app.log.error(error);
  process.exit(1);
});
