const API_BASE = "https://api.github.com";

export type RepoRef = { owner: string; repo: string; branch: string };

export async function githubJson<T>(path: string): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/vnd.github+json",
    "User-Agent": "amin-mcp/0.1.0",
    "X-GitHub-Api-Version": "2022-11-28"
  };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;

  const response = await fetch(`${API_BASE}${path}`, {
    headers,
    signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`GitHub ${response.status}: ${detail.slice(0, 300)}`);
  }
  return response.json() as Promise<T>;
}

export async function readRepoJson<T>(ref: RepoRef, path: string): Promise<T> {
  const encodedPath = path.split("/").map(encodeURIComponent).join("/");
  const file = await githubJson<{ content?: string; encoding?: string }>(
    `/repos/${ref.owner}/${ref.repo}/contents/${encodedPath}?ref=${encodeURIComponent(ref.branch)}`
  );
  if (!file.content || file.encoding !== "base64") throw new Error(`Unable to decode ${path}`);
  return JSON.parse(Buffer.from(file.content.replace(/\n/g, ""), "base64").toString("utf8")) as T;
}
