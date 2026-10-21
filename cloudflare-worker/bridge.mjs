// Flowseal v1.11.2 DC_DEFAULT_IPS. Never accept an arbitrary destination or port.
export const routes = Object.freeze({
  1: '149.154.175.50', 2: '149.154.167.51', 3: '149.154.175.100',
  4: '149.154.167.91', 5: '149.154.171.5', 203: '91.105.192.100',
});
const PACKET = 4 * 1024 * 1024 + 4;
export const defaults = Object.freeze({ sessions: 6, connect: 6000, init: 10000,
  write: 10000, idle: 300000, queue: PACKET, messages: 64,
  // Cloudflare WebSocket.send has no drain API; do not remove this lifetime
  // safety budget without implementing verifiable downstream flow control.
  // The default Worker remains experimental for sustained media transfer.
  bytes: 16 * 1024 * 1024 });

export function destination(request, token) {
  if (!/^[a-f0-9]{32,64}$/.test(token ?? '')) return null;
  if (request.method !== 'GET' || request.headers.get('Upgrade')?.toLowerCase() !== 'websocket' ||
      !request.headers.get('Sec-WebSocket-Protocol')?.split(',').map(x => x.trim()).includes('binary')) return null;
  const url = new URL(request.url);
  const expected = `/${token}/apiws`;
  if (url.protocol !== 'https:' || url.pathname.length !== expected.length) return null;
  let mismatch = 0;
  for (let i = 0; i < expected.length; i++) mismatch |= url.pathname.charCodeAt(i) ^ expected.charCodeAt(i);
  if (mismatch || [...url.searchParams].length !== 2 || url.searchParams.getAll('dc').length !== 1 ||
      url.searchParams.getAll('dst').length !== 1) return null;
  const dc = url.searchParams.get('dc');
  return Object.hasOwn(routes, dc) && routes[dc] === url.searchParams.get('dst') ? routes[dc] : null;
}

/** Injectable platform edges; tests do not replace the deployed route table. */
export function createHandler({ dial, pair, upgrade, limits = defaults }) {
  let active = 0;
  const unavailable = (status, reason, retry) => new Response(null, { status, headers: {
    'Cache-Control': 'no-store', 'X-TGWS-Error': reason,
    ...(retry ? { 'Retry-After': String(retry) } : {}),
  } });
  class Deadline extends Error {}
  return async (request, env) => {
    if (!/^[a-f0-9]{32,64}$/.test(env.TGWS_RELAY_TOKEN ?? '')) return unavailable(503, 'CONFIGURATION');
    const target = destination(request, env.TGWS_RELAY_TOKEN);
    if (!target) return new Response(null, { status: 404 });
    if (active >= limits.sessions) return unavailable(503, 'CAPACITY', 2);
    active++;
    let socket, server, reader, writer, idle, init;
    let ended = false, released = false, listensForClose = false, first = true, queued = 0, messages = 0, bytes = 0;
    let tcpClosed = true, websocketClosed = true, accepted = false;
    let tail = Promise.resolve();
    const timers = new Map();
    const ignore = promise => Promise.resolve(promise).catch(() => {});
    const release = () => {
      // A close event is not proof that upstream TCP has finished closing.
      // Do not admit a replacement until both platform resources are closed.
      if (ended && tcpClosed && websocketClosed && !released) { released = true; active--; }
    };
    // Fixed, non-sensitive close categories are visible to our Android client.
    // Never pass exception messages, request URLs, private endpoints, or tokens.
    function finish(code = 1011, reason = 'RELAY_ERROR') {
      if (ended) return;
      ended = true;
      clearTimeout(idle); clearTimeout(init);
      for (const [timer, reject] of timers) { clearTimeout(timer); reject(new Error('Closed')); }
      timers.clear();
      // Keep a capacity slot while the platform still owns a closing WS buffer.
      try { server?.close(code, reason); } catch {}
      if (!listensForClose || server?.readyState === 3) websocketClosed = true;
      try { if (socket) ignore(Promise.resolve(socket.close()).then(() => { tcpClosed = true; release(); })); } catch {}
      try { if (reader) ignore(reader.cancel().finally(() => { try { reader.releaseLock(); } catch {} })); } catch {}
      try { if (writer) ignore(writer.abort()); } catch {}
      // Deadline cancellation settles our queued writes even if the platform
      // write is still pending; release the stream lock only after that chain.
      ignore(tail.finally(() => { try { writer?.releaseLock(); } catch {} }));
      release();
    }
    const touch = () => { clearTimeout(idle); idle = setTimeout(() => finish(1011, 'IDLE_TIMEOUT'), limits.idle); };
    function deadline(promise, millis) {
      let timer;
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => { timers.delete(timer); reject(new Deadline()); }, millis);
        timers.set(timer, reject);
      });
      return Promise.race([promise, timeout]).finally(() => { clearTimeout(timer); timers.delete(timer); });
    }
    function charge(size) {
      if (bytes + size > limits.bytes) { finish(1008, 'BYTE_LIMIT'); return false; }
      bytes += size;
      return true;
    }
    try {
      socket = dial(target);
      tcpClosed = false;
      // closed may reject before opened; observe it even on setup failure.
      socket.closed.then(() => { tcpClosed = true; release(); }, () => { tcpClosed = true; finish(1011, 'TCP_ERROR'); release(); });
      await deadline(socket.opened, limits.connect);
      if (ended) throw new Error('Closed during setup');
      const endpoints = pair();
      server = endpoints[1];
      // Configure binary delivery and handlers before accept can dispatch data.
      server.binaryType = 'arraybuffer';
      reader = socket.readable.getReader();
      writer = socket.writable.getWriter();
      websocketClosed = false;
      server.addEventListener('close', () => { websocketClosed = true; finish(1000, 'CLIENT_CLOSED'); release(); });
      server.addEventListener('error', () => finish(1011, 'WS_ERROR'));
      listensForClose = true;
      server.addEventListener('message', event => {
        if (ended) return;
        if (!(event.data instanceof ArrayBuffer)) { finish(1003, 'BAD_FRAME'); return; }
        const size = event.data.byteLength;
        if (size > PACKET) { finish(1009, 'FRAME_LIMIT'); return; }
        if (first && size !== 64) { finish(1002, 'BAD_INIT'); return; }
        if (!size) return;
        if (queued + size > limits.queue) { finish(1009, 'QUEUE_LIMIT'); return; }
        if (messages >= limits.messages) { finish(1009, 'MESSAGE_LIMIT'); return; }
        if (!charge(size)) return;
        first = false;
        clearTimeout(init);
        queued += size; messages++;
        const data = new Uint8Array(event.data);
        tail = tail.then(async () => {
          if (ended) return;
          await deadline(writer.write(data), limits.write);
          if (!ended) touch();
        }).catch(error => finish(1011, error instanceof Deadline ? 'WRITE_TIMEOUT' : 'TCP_ERROR'))
          .finally(() => { queued -= size; messages--; });
      });
      init = setTimeout(() => finish(1002, 'INIT_TIMEOUT'), limits.init);
      touch();
      server.accept();
      accepted = true;
      void (async () => {
        try {
          while (!ended) {
            const { value, done } = await reader.read();
            if (done) { finish(1000, 'TCP_EOF'); break; }
            if (!value?.byteLength) continue;
            if (first) { finish(1002, 'BAD_INIT'); break; }
            if (!charge(value.byteLength)) break;
            // Small WS frames avoid copying a whole downstream TCP chunk per send.
            for (let i = 0; i < value.byteLength && !ended; i += 65536) server.send(value.subarray(i, i + 65536));
            touch();
          }
        } catch { finish(1011, 'TCP_ERROR'); }
        finally { try { reader.releaseLock(); } catch {} }
      })();
      return upgrade(endpoints[0]);
    } catch (error) {
      // A pair which failed before accept has no external WS peer/output queue
      // and cannot be expected to emit a close event. TCP is still accounted.
      if (!accepted) { listensForClose = false; websocketClosed = true; }
      finish();
      return unavailable(error instanceof Deadline ? 504 : 502,
        error instanceof Deadline ? 'UPSTREAM_TIMEOUT' : 'UPSTREAM_CONNECT', 1);
    }
  };
}
