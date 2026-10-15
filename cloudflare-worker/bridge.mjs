// Flowseal v1.11.2 DC_DEFAULT_IPS. Never accept an arbitrary destination or port.
export const routes = Object.freeze({
  1: '149.154.175.50', 2: '149.154.167.51', 3: '149.154.175.100',
  4: '149.154.167.91', 5: '149.154.171.5', 203: '91.105.192.100',
});
const PACKET = 4 * 1024 * 1024 + 4;
export const defaults = Object.freeze({ sessions: 4, connect: 6000, init: 10000,
  write: 10000, idle: 300000, queue: PACKET, messages: 64,
  // Workers WebSocket.send has no drain API. A lifetime byte budget bounds
  // runtime-owned send buffers; this experimental endpoint is not a media relay.
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
  return async (request, env) => {
    if (!/^[a-f0-9]{32,64}$/.test(env.TGWS_RELAY_TOKEN ?? '')) return new Response(null, { status: 503 });
    const target = destination(request, env.TGWS_RELAY_TOKEN);
    if (!target) return new Response(null, { status: 404 });
    if (active >= limits.sessions) return new Response(null, { status: 503 });
    active++;
    let socket, server, reader, writer, idle, init;
    let ended = false, released = false, listensForClose = false, first = true, queued = 0, messages = 0, bytes = 0;
    let tail = Promise.resolve();
    const timers = new Map();
    const ignore = promise => Promise.resolve(promise).catch(() => {});
    const release = () => { if (!released) { released = true; active--; } };
    function finish(code = 1011) {
      if (ended) return;
      ended = true;
      clearTimeout(idle); clearTimeout(init);
      for (const [timer, reject] of timers) { clearTimeout(timer); reject(new Error('Closed')); }
      timers.clear();
      // Keep a capacity slot while the platform still owns a closing WS buffer.
      if (!listensForClose) release();
      try { server?.close(code, code === 1000 ? 'Closed' : 'Relay ended'); } catch {}
      try { if (socket) ignore(socket.close()); } catch {}
      try { if (reader) ignore(reader.cancel()); } catch {}
      try { if (writer) ignore(writer.abort()); } catch {}
    }
    const touch = () => { clearTimeout(idle); idle = setTimeout(() => finish(), limits.idle); };
    function deadline(promise, millis) {
      let timer;
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => { timers.delete(timer); reject(new Error('Deadline')); }, millis);
        timers.set(timer, reject);
      });
      return Promise.race([promise, timeout]).finally(() => { clearTimeout(timer); timers.delete(timer); });
    }
    function charge(size) {
      if (bytes + size > limits.bytes) { finish(1008); return false; }
      bytes += size;
      return true;
    }
    try {
      socket = dial(target);
      await deadline(socket.opened, limits.connect);
      const endpoints = pair();
      server = endpoints[1];
      server.accept();
      server.binaryType = 'arraybuffer';
      reader = socket.readable.getReader();
      writer = socket.writable.getWriter();
      server.addEventListener('close', () => { release(); finish(1000); });
      server.addEventListener('error', () => finish());
      listensForClose = true;
      server.addEventListener('message', event => {
        if (ended) return;
        if (!(event.data instanceof ArrayBuffer)) { finish(1003); return; }
        const size = event.data.byteLength;
        if (size > PACKET) { finish(1009); return; }
        if (first && size !== 64) { finish(1002); return; }
        if (!size) return;
        if (queued + size > limits.queue || messages >= limits.messages) { finish(1009); return; }
        if (!charge(size)) return;
        first = false;
        clearTimeout(init);
        queued += size; messages++;
        const data = new Uint8Array(event.data);
        tail = tail.then(async () => {
          if (ended) return;
          await deadline(writer.write(data), limits.write);
          if (!ended) touch();
        }).catch(() => finish()).finally(() => { queued -= size; messages--; });
      });
      init = setTimeout(() => finish(1002), limits.init);
      touch();
      void (async () => {
        try {
          while (!ended) {
            const { value, done } = await reader.read();
            if (done) { finish(1000); break; }
            if (!value?.byteLength) continue;
            if (first) { finish(1002); break; }
            if (!charge(value.byteLength)) break;
            // Small WS frames avoid copying a whole downstream TCP chunk per send.
            for (let i = 0; i < value.byteLength && !ended; i += 65536) server.send(value.subarray(i, i + 65536));
            touch();
          }
        } catch { finish(); }
        finally { try { reader.releaseLock(); } catch {} }
      })();
      socket.closed.catch(() => finish());
      return upgrade(endpoints[0]);
    } catch { finish(); return new Response(null, { status: 502 }); }
  };
}
