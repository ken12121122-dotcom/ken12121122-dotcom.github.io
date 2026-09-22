const DEFAULT_TIMEOUT_MS = 8000;

export class AndroidControlClient {
  constructor({ baseUrl, token, timeoutMs = DEFAULT_TIMEOUT_MS }) {
    if (!baseUrl) throw new Error('AMIN_PHONE_URL is required');
    if (!token) throw new Error('AMIN_PHONE_TOKEN is required');
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.token = token;
    this.timeoutMs = timeoutMs;
  }

  async status() {
    return this.#request('/v1/status');
  }

  async actions() {
    return this.#request('/v1/actions');
  }

  async execute(action, parameters = {}) {
    if (!action || typeof action !== 'string') {
      throw new Error('action must be a non-empty string');
    }
    return this.#request(`/v1/actions/${encodeURIComponent(action)}`, {
      method: 'POST',
      body: JSON.stringify({ parameters })
    });
  }

  async #request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...options,
        headers: {
          Authorization: `Bearer ${this.token}`,
          'Content-Type': 'application/json',
          ...(options.headers ?? {})
        },
        signal: controller.signal
      });

      const text = await response.text();
      let payload;
      try {
        payload = text ? JSON.parse(text) : {};
      } catch {
        payload = { raw: text };
      }

      if (!response.ok) {
        const error = new Error(payload?.message || `Android API returned HTTP ${response.status}`);
        error.status = response.status;
        error.payload = payload;
        throw error;
      }
      return payload;
    } catch (error) {
      if (error.name === 'AbortError') {
        throw new Error(`Android API timeout after ${this.timeoutMs} ms`);
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }
}
