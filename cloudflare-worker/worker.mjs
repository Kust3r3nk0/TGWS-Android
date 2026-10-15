import { connect } from 'cloudflare:sockets';
import { createHandler } from './bridge.mjs';

const handler = createHandler({
  dial: hostname => connect({ hostname, port: 443 }, { secureTransport: 'off', allowHalfOpen: false }),
  pair: () => new WebSocketPair(),
  upgrade: client => new Response(null, { status: 101, webSocket: client,
    headers: { 'Sec-WebSocket-Protocol': 'binary' } }),
});

export default { fetch: (request, env) => handler(request, env) };
