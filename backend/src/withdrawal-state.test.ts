import test from "node:test";
import assert from "node:assert/strict";

const transitions = {
  pending: ["approved", "rejected"],
  approved: ["completed", "failed"],
  completed: [],
  rejected: [],
  failed: ["approved"]
};

test("withdrawal state machine allows only expected transitions", () => {
  assert.equal(transitions.pending.includes("approved"), true);
  assert.equal(transitions.pending.includes("completed"), false);
  assert.equal(transitions.approved.includes("completed"), true);
  assert.equal(transitions.completed.includes("approved"), false);
  assert.equal(transitions.rejected.includes("approved"), false);
});
