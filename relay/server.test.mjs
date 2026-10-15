import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { readFileSync } from 'node:fs';
import net from 'node:net';
import https from 'node:https';
import { Duplex } from 'node:stream';
import WebSocket from 'ws';
import { createRelay, destination, routes } from './server.mjs';

const token = '0123456789abcdef0123456789abcdef'; // Public test fixture, never deployed.
const tls = { pfx: readFileSync(new URL('../app/src/test/resources/websocket/localhost.p12', import.meta.url)), passphrase: 'test-only-password' };
const ca = readFileSync(new URL('../app/src/test/resources/websocket/localhost.crt', import.meta.url));
const path = `/${token}/apiws?dst=149.154.167.51&dc=2`;
const request = url => ({ method: 'GET', headers: { upgrade: 'websocket' }, url });
const waitFor = async predicate => {
  const deadline = Date.now() + 2500;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error('Condition timed out');
    await new Promise(resolve => setTimeout(resolve, 5));
  }
};
async function fixture(t, options = {}) {
  const accepted = [];
  const peer = net.createServer(socket => { accepted.push(socket); socket.pipe(socket); });
  peer.listen(0, '127.0.0.1');
  await once(peer, 'listening');
  let calls = 0;
  const relay = createRelay({ tls, token, dial: address => {
    assert.equal(address, routes[2]); calls++;
    return net.connect(peer.address().port, '127.0.0.1');
  }, ...options });
  relay.server.listen(0, '127.0.0.1');
  await once(relay.server, 'listening');
  t.after(async () => {
    await relay.close();
    for (const socket of accepted) socket.destroy();
    await new Promise(resolve => peer.close(resolve));
  });
  const url = `wss://localhost:${relay.server.address().port}`;
  const connect = async (suffix = path, extra = {}) => {
    const ws = new WebSocket(url + suffix, { ca, lookup: (_host, opts, cb) =>
      opts.all ? cb(null, [{ address: '127.0.0.1', family: 4 }]) : cb(null, '127.0.0.1', 4), ...extra });
    await once(ws, 'open');
    return ws;
  };
  return { relay, connect, url, calls: () => calls };
}

test('route guard accepts only exact Flowseal destination/DC pairs and protected paths', () => {
  for (const [dc, dst] of Object.entries(routes)) assert.equal(destination(request(`/${token}/apiws?dst=${dst}&dc=${dc}`), token), dst);
  for (const value of [path.replace(token, '0'.repeat(32)), path + '&dc=2', path + '&extra=x',
    path.replace('149.154.167.51', '127.0.0.1'), path.replace('dc=2', 'dc=10002'), '/apiws?dst=x&dc=2']) {
    assert.equal(destination(request(value), token), null);
  }
  assert.equal(destination({ ...request(path), method: 'POST' }, token), null);
  assert.throws(() => createRelay({ tls, token: 'weak' }));
});

test('real TLS WSS relay transports header, fragmentation, coalescing and binary bytes in both directions', async t => {
  const { connect, relay } = await fixture(t);
  const ws = await connect();
  const header = Buffer.alloc(64, 7);
  let answer = Buffer.alloc(0);
  ws.on('message', data => { answer = Buffer.concat([answer, data]); });
  ws.send(header);
  ws.send(Buffer.from([1, 2]), { fin: false });
  ws.send(Buffer.from([3, 4]), { fin: true });
  ws.send(Buffer.alloc(65536, 8));
  const expected = Buffer.concat([header, Buffer.from([1, 2, 3, 4]), Buffer.alloc(65536, 8)]);
  await waitFor(() => answer.length === expected.length);
  assert.deepEqual(answer, expected);
  ws.close();
  await once(ws, 'close');
  await waitFor(() => relay.activeSessions() === 0);
});

test('invalid credentials are rejected before TCP dial', async t => {
  const { relay, calls } = await fixture(t);
  const status = await new Promise((resolve, reject) => {
    https.get({ hostname: '127.0.0.1', servername: 'localhost', port: relay.server.address().port,
      path: path.replace(token, 'f'.repeat(32)), ca, headers: { Upgrade: 'websocket', Connection: 'Upgrade' } }, response => {
      response.resume(); resolve(response.statusCode);
    }).on('error', reject);
  });
  assert.equal(status, 404);
  assert.equal(calls(), 0);
});

test('untrusted TLS is rejected without disabling certificate validation', async t => {
  const { connect } = await fixture(t);
  await assert.rejects(connect(path, { ca: undefined }), error => ['DEPTH_ZERO_SELF_SIGNED_CERT', 'SELF_SIGNED_CERT_IN_CHAIN'].includes(error.code));
});

test('text and invalid initial binary data terminate sessions', async t => {
  const { connect, relay } = await fixture(t);
  for (const payload of ['not binary', Buffer.alloc(63)]) {
    const ws = await connect();
    const closed = once(ws, 'close');
    ws.send(payload);
    await closed;
    await waitFor(() => relay.activeSessions() === 0);
  }
});

test('connection capacity rejects excess upgrade before opening another TCP socket', async t => {
  const { connect, calls, relay } = await fixture(t, { maxSessions: 1 });
  const first = await connect();
  await assert.rejects(connect(), /503/);
  assert.equal(calls(), 1);
  first.terminate();
  await waitFor(() => relay.activeSessions() === 0);
});

test('TCP connect deadline releases an unconnected socket and pending capacity', async t => {
  const stalled = new net.Socket();
  const { connect, relay } = await fixture(t, { connectMillis: 40, dial: () => stalled });
  await assert.rejects(connect());
  await waitFor(() => relay.activeSessions() === 0);
  assert.equal(stalled.destroyed, true);
});

test('idle deadline closes both sides despite healthy control frames', async t => {
  const { connect, relay } = await fixture(t, { idleMillis: 100, pingMillis: 20 });
  const ws = await connect();
  await once(ws, 'close');
  await waitFor(() => relay.activeSessions() === 0);
});

test('oversized messages terminate the relay and release capacity', async t => {
  const { connect, relay } = await fixture(t);
  const ws = await connect();
  ws.send(Buffer.alloc(64));
  await once(ws, 'message');
  const closed = once(ws, 'close');
  ws.send(Buffer.alloc(4 * 1024 * 1024 + 5), () => {});
  await closed;
  await waitFor(() => relay.activeSessions() === 0);
});

test('stalled upstream write deadline closes a backpressured session', async t => {
  class BlockedTcp extends Duplex {
    _read() {}
    _write(_chunk, _encoding, _callback) {} // Deliberately no write completion.
    setNoDelay() {}
    setTimeout() {}
  }
  const blocked = new BlockedTcp();
  const { connect, relay } = await fixture(t, { writeMillis: 40, dial: () => {
    queueMicrotask(() => blocked.emit('connect'));
    return blocked;
  } });
  const ws = await connect();
  const closed = once(ws, 'close');
  ws.send(Buffer.alloc(64));
  await closed;
  await waitFor(() => relay.activeSessions() === 0);
  assert.equal(blocked.destroyed, true);
});

test('ongoing backpressured transfer can last longer than the per-write deadline', async t => {
  class SlowTcp extends Duplex {
    _read() {}
    _write(_chunk, _encoding, callback) { setTimeout(callback, 15); }
    setNoDelay() {}
    setTimeout() {}
  }
  const slow = new SlowTcp();
  const { connect, relay } = await fixture(t, { writeMillis: 80, dial: () => {
    queueMicrotask(() => slow.emit('connect'));
    return slow;
  } });
  const ws = await connect();
  ws.send(Buffer.alloc(64));
  for (let index = 0; index < 20; index++) ws.send(Buffer.alloc(1024));
  await new Promise(resolve => setTimeout(resolve, 380));
  assert.equal(ws.readyState, WebSocket.OPEN);
  assert.equal(relay.activeSessions(), 1);
  ws.close();
  await once(ws, 'close');
});
