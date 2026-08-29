import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { githubJson, readRepoJson, type RepoRef } from "./github.js";

const repo: RepoRef = {
  owner: process.env.AMIN_GITHUB_OWNER ?? "ken12121122-dotcom",
  repo: process.env.AMIN_GITHUB_REPO ?? "ken12121122-dotcom.github.io",
  branch: process.env.AMIN_GITHUB_BRANCH ?? "main"
};

const runtimePath = "amin-vault/runtime-manifest.json";
const nativePath = "amin-vault/native-release-manifest.json";

function ok(data: unknown, message: string) {
  return {
    structuredContent: data as Record<string, unknown>,
    content: [{ type: "text" as const, text: message }]
  };
}

export function createServer(): McpServer {
  const server = new McpServer({ name: "Amin Project MCP", version: "0.1.0" });

  server.registerTool(
    "get_project_status",
    {
      title: "Get Amin project status",
      description: "Use this when you need a read-only snapshot of the Amin repository, current branch, latest commit, and release manifests.",
      inputSchema: {},
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: true, idempotentHint: true }
    },
    async () => {
      const [repository, commits, runtime, native] = await Promise.all([
        githubJson<Record<string, unknown>>(`/repos/${repo.owner}/${repo.repo}`),
        githubJson<Array<Record<string, any>>>(`/repos/${repo.owner}/${repo.repo}/commits?sha=${encodeURIComponent(repo.branch)}&per_page=1`),
        readRepoJson<Record<string, unknown>>(repo, runtimePath),
        readRepoJson<Record<string, unknown>>(repo, nativePath)
      ]);
      const data = {
        repository: `${repo.owner}/${repo.repo}`,
        branch: repo.branch,
        visibility: repository.visibility,
        latestCommit: commits[0] ? {
          sha: commits[0].sha,
          message: commits[0].commit?.message,
          date: commits[0].commit?.committer?.date,
          url: commits[0].html_url
        } : null,
        runtimeManifest: runtime,
        nativeManifest: native,
        checkedAt: new Date().toISOString()
      };
      return ok(data, `Amin project status loaded from ${data.repository}@${repo.branch}.`);
    }
  );

  server.registerTool(
    "get_latest_version",
    {
      title: "Get latest Amin versions",
      description: "Use this when you need the current Runtime and Android release manifests without changing any files or releases.",
      inputSchema: {},
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: true, idempotentHint: true }
    },
    async () => {
      const [runtime, native] = await Promise.all([
        readRepoJson<Record<string, unknown>>(repo, runtimePath),
        readRepoJson<Record<string, unknown>>(repo, nativePath)
      ]);
      return ok({ runtime, native, checkedAt: new Date().toISOString() }, "Latest Amin release manifests loaded.");
    }
  );

  server.registerTool(
    "get_recent_changes",
    {
      title: "Get recent Amin changes",
      description: "Use this when you need recent commit summaries for change-oriented analysis or test planning.",
      inputSchema: { limit: z.number().int().min(1).max(20).default(5) },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: true, idempotentHint: true }
    },
    async ({ limit }) => {
      const commits = await githubJson<Array<Record<string, any>>>(
        `/repos/${repo.owner}/${repo.repo}/commits?sha=${encodeURIComponent(repo.branch)}&per_page=${limit}`
      );
      const data = commits.map((item) => ({
        sha: item.sha,
        message: item.commit?.message,
        author: item.commit?.author?.name,
        date: item.commit?.author?.date,
        url: item.html_url
      }));
      return ok({ commits: data, count: data.length }, `Loaded ${data.length} recent commits.`);
    }
  );

  server.registerTool(
    "create_test_plan",
    {
      title: "Create a safe test plan",
      description: "Use this when you need a read-only recommended test plan based on a described change. This tool never executes tests or modifies the repository.",
      inputSchema: {
        changeSummary: z.string().min(3).max(2000),
        riskLevel: z.enum(["low", "medium", "high"]).default("medium")
      },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false, idempotentHint: true }
    },
    async ({ changeSummary, riskLevel }) => {
      const baseline = ["build and type checks", "changed-feature functional test", "core regression test", "save and restore verification"];
      const interference = ["offline and weak network", "rapid repeated input", "foreground/background switching", "permission denial and revocation", "runtime/native version mismatch"];
      if (riskLevel !== "low") interference.push("memory pressure and long-duration operation");
      if (riskLevel === "high") interference.push("update interruption and rollback verification");
      return ok({ changeSummary, riskLevel, baseline, interference, executionStatus: "planned_only" }, "A safe, non-executing test plan was created.");
    }
  );

  return server;
}
