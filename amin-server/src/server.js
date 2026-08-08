import http from 'node:http';
import { AndroidControlClient } from './android-client.js';

const HOST = process.env.AMIN_SERVER_HOST || '127.0.0.1';
const PORT = Number(process.env.AMIN_SERVER_PORT || 3000);

const android = new AndroidControlClient({
  baseUrl: process.env.AMIN_PHONE_URL,
  token: process.env.AMIN_PHONE_TOKEN
});

const json = (res, status, body) => {
  const data = JSON.stringify(body, null, 2);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data),
    'Cache-Control': 'no-store'
  });
  res.end(data);
};

const readJson = async (req) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (chunks.length === 0) return {};
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : {};
};

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || `${HOST}:${PORT}`}`);

    if (req.method === 'GET' && url.pathname === '/health') {
      return json(res, 200, {
        status: 'ok',
        service: 'amin-node-server',
        androidConfigured: Boolean(process.env.AMIN_PHONE_URL && process.env.AMIN_PHONE_TOKEN)
      });
    }

    if (req.method === 'GET' && url.pathname === '/android/status') {
      return json(res, 200, await android.status());
    }

    if (req.method === 'GET' && url.pathname === '/android/actions') {
      return json(res, 200, await android.actions());
    }

    const actionMatch = url.pathname.match(/^\/android\/actions\/([^/]+)$/);
    if (req.method === 'POST' && actionMatch) {
      const body = await readJson(req);
      const action = decodeURIComponent(actionMatch[1]);
      return json(res, 200, await android.execute(action, body.parameters ?? {}));
    }

    return json(res, 404, {
      success: false,
      code: 'NOT_FOUND',
      message: 'Unknown endpoint'
    });
  } catch (error) {
    const status = Number(error.status) || 502;
    return json(res, status, {
      success: false,
      code: 'UPSTREAM_ERROR',
      message: error.message,
      upstream: error.payload ?? null
    });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Amin Node Server listening on http://${HOST}:${PORT}`);
  console.log(`Android target: ${process.env.AMIN_PHONE_URL || '(not configured)'}`);
});
