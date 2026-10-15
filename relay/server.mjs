import https from 'node:https';
import net from 'node:net';
import { timingSafeEqual } from 'node:crypto';
import { WebSocketServer, createWebSocketStream } from 'ws';

// Flowseal v1.11.2 DC_DEFAULT_IPS. This endpoint is never an arbitrary TCP proxy.
export const routes = Object.freeze({
  1: '149.154.175.50', 2: '149.154.167.51', 3: '149.154.175.100',
  4: '149.154.167.91', 5: '149.154.171.5', 203: '91.105.192.100',
});

export function destination(request, token) {
  if (request.method !== 'GET' || request.headers.upgrade?.toLowerCase() !== 'websocket') return null;
  let url;
  try { url = new URL(request.url, 'https://relay.invalid'); } catch { return null; }
  const expected = Buffer.from(`/${token}/apiws`);
  const actual = Buffer.from(url.pathname);
  if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) return null;
  const query = [...url.searchParams];
  if (query.length !== 2 || url.searchParams.getAll('dc').length !== 1 || url.searchParams.getAll('dst').length !== 1) return null;
  const dc = url.searchParams.get('dc');
  return Object.hasOwn(routes, dc) && routes[dc] === url.searchParams.get('dst') ? routes[dc] : null;
}

/** TLS terminates here; stream backpressure bounds both directions. No packet/URL logging. */
export function createRelay({ tls, token, maxSessions = 8, connectMillis = 8000, idleMillis = 300000,
  pingMillis = 60000, pongMillis = 10000, writeMillis = 10000, dial = address => net.connect(443, address) }) {
  if (!/^[a-f0-9]{32,64}$/.test(token ?? '')) throw new Error('Set a random 32–64 character HEX relay token');
  if (!tls || !Number.isInteger(maxSessions) || maxSessions < 1 || maxSessions > 64) throw new Error('Invalid relay configuration');
  const sessions = new Set();
  const websocket = new WebSocketServer({ noServer: true, maxPayload: 4 * 1024 * 1024 + 4, perMessageDeflate: false });
  const server = https.createServer({ ...tls, minVersion: 'TLSv1.2', handshakeTimeout: 8000 }, (_request, response) => {
    response.writeHead(404).end();
  });
  server.headersTimeout = 8000;
  server.requestTimeout = 10000;
  server.maxConnections = maxSessions * 4;
  server.on('clientError', (_error, socket) => socket.destroy());
  server.on('tlsClientError', () => {}); // No request, certificate or provider details in logs.
  server.on('upgrade', (request, client, head) => {
    const target = destination(request, token);
    if (!target || sessions.size >= maxSessions) {
      client.end(`HTTP/1.1 ${target ? '503 Service Unavailable' : '404 Not Found'}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`, () => client.destroy());
      return;
    }
    let tcp, ws, stream, ping, pong, writeTimer, initTimer;
    let ended = false;
    const setup = setTimeout(() => finish(), connectMillis);
    const stop = () => finish();
    sessions.add(stop);
    function finish() {
      if (ended) return;
      ended = true;
      clearTimeout(setup);
      clearInterval(ping);
      clearTimeout(pong);
      clearTimeout(writeTimer);
      clearTimeout(initTimer);
      stream?.destroy();
      ws?.terminate();
      tcp?.destroy();
      client.destroy();
      sessions.delete(stop);
    }
    client.on('error', finish);
    client.on('close', finish);
    try { tcp = dial(target); } catch { finish(); return; }
    tcp.on('error', finish);
    tcp.on('close', finish);
    tcp.setNoDelay(true);
    tcp.setTimeout(idleMillis, finish);
    tcp.once('connect', () => {
      if (ended) return;
      clearTimeout(setup);
      try {
        websocket.handleUpgrade(request, client, head, connected => {
          ws = connected;
          let first = true;
          initTimer = setTimeout(finish, 10000);
          ws.on('error', finish);
          ws.on('close', finish);
          ws.on('message', (data, binary) => {
            if (!binary || (first && data.length !== 64)) finish();
            clearTimeout(initTimer);
            first = false;
          });
          stream = createWebSocketStream(ws, { highWaterMark: 64 * 1024 });
          stream.on('error', finish);
          // A stalled write in either direction must not retain a session forever.
          const drain = () => {
            if (tcp.writableLength === 0 && stream.writableLength === 0) {
              clearTimeout(writeTimer);
              writeTimer = undefined;
            }
          };
          tcp.on('drain', drain);
          stream.on('drain', drain);
          // A small write need not emit drain; inspect outstanding bytes on the same bounded timer.
          const checkWrite = () => {
            if (tcp.writableLength > 0 || stream.writableLength > 0) finish();
            writeTimer = undefined;
          };
          const resetWrite = () => {
            if (writeTimer === undefined) writeTimer = setTimeout(checkWrite, writeMillis);
          };
          tcp.on('data', resetWrite);
          stream.on('data', resetWrite);
          stream.pipe(tcp).pipe(stream);
          ws.on('pong', () => { clearTimeout(pong); pong = undefined; });
          ping = setInterval(() => {
            if (pong !== undefined) return;
            try { ws.ping(); pong = setTimeout(finish, pongMillis); } catch { finish(); }
          }, pingMillis);
        });
      } catch { finish(); }
    });
  });
  return {
    server,
    activeSessions: () => sessions.size,
    async close() {
      for (const stop of sessions) stop();
      websocket.close();
      server.closeIdleConnections();
      if (server.listening) await new Promise(resolve => server.close(resolve));
    },
  };
}
