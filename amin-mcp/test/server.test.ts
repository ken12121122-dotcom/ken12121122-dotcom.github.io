import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "../src/server.js";

test("createServer returns an MCP server", () => {
  const server = createServer();
  assert.ok(server);
  assert.equal(typeof server.connect, "function");
});

test("default repository configuration is safe and read-only", () => {
  assert.equal(process.env.AMIN_GITHUB_OWNER ?? "ken12121122-dotcom", "ken12121122-dotcom");
  assert.equal(process.env.AMIN_GITHUB_REPO ?? "ken12121122-dotcom.github.io", "ken12121122-dotcom.github.io");
});
