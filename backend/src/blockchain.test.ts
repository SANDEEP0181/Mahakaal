import test from "node:test";
import assert from "node:assert/strict";
import { DisabledBlockchainProvider } from "./blockchain.js";

test("disabled blockchain provider never broadcasts", async () => {
  const provider = new DisabledBlockchainProvider();
  await assert.rejects(
    () => provider.broadcast({
      withdrawalId: "test",
      userId: "test",
      address: "0x0000000000000000000000000000000000000001",
      network: "kaal-testnet",
      amount: "1"
    }),
    /not enabled/
  );
});

test("disabled provider accepts a valid EVM address", async () => {
  const provider = new DisabledBlockchainProvider();
  assert.equal(
    await provider.validateAddress("0x0000000000000000000000000000000000000001", "kaal-testnet"),
    true
  );
});
