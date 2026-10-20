import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { Duplex } from 'node:stream';
import { createHandler, destination, defaults, routes } from './bridge.mjs';
import { routes as originRoutes } from '../relay/server.mjs';

const token = 'ab'.repeat(24);
const env = { TGWS_RELAY_TOKEN: token };
const request = (path = `/${token}/apiws?dst=149.154.167.51&dc=2`, headers = {}) =>
  new Request(`https://relay.example${path}`, { headers: { Upgrade: 'websocket', 'Sec-WebSocket-Protocol': 'binary', ...headers } });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(condition) {
  const end = Date.now() + 1500;
  while (!condition()) { assert.ok(Date.now() < end, 'Condition timed out'); await pause(2); }
}
class WebSocketStub {
  callbacks = new Map(); sent = []; code = null; closeReason = null;
  accept() {}
  addEventListener(type, fn) { this.callbacks.set(type, fn); }
  emit(type, data) { this.callbacks.get(type)?.({ data }); }
  close(code, reason) { if (this.code === null) { this.code = code; this.closeReason = reason; this.emit('close'); } }
  send(data) { this.sent.push(Uint8Array.from(data)); }
}
function fixture({ opened = Promise.resolve(), socketClosed = new Promise(() => {}), write, read, limits = {} } = {}) {
  const server = new WebSocketStub();
  const writes = []; let closed = 0, dials = 0, input;
  const socket = {
    opened, closed: socketClosed,
    readable: new ReadableStream({ start(controller) { input = controller; if (read) read(controller); } }),
    writable: new WritableStream({ write(data) { writes.push(Uint8Array.from(data)); return write?.(data); } }),
    close() { closed++; },
  };
  const handler = createHandler({ dial() { dials++; return socket; }, pair: () => [null, server],
    upgrade: () => ({ status: 101 }), limits: { ...defaults, connect: 100, init: 100, write: 100, idle: 200, ...limits } });
  return { handler, server, writes, input, get closed() { return closed; }, get dials() { return dials; } };
}

test('routes match existing origin; canonical DC/IP pairs only', () => {
  assert.deepEqual(routes, originRoutes);
  for (const [dc, ip] of Object.entries(routes)) assert.equal(destination(request(`/${token}/apiws?dst=${ip}&dc=${dc}`), token), ip);
  for (const query of ['dst=127.0.0.1&dc=2','dst=149.154.167.51&dc=-2','dst=149.154.167.51&dc=02',
    'dst=149.154.167.51&dc=2&dc=2','dst=149.154.167.51&dc=2&port=80','dst=149.154.167.51'])
    assert.equal(destination(request(`/${token}/apiws?${query}`), token), null);
});
test('key, exact path, HTTPS, Upgrade and binary protocol required', () => {
  for (const req of [request('/apiws?dst=149.154.167.51&dc=2'), request(`/${'cd'.repeat(24)}/apiws?dst=149.154.167.51&dc=2`),
    request(undefined, { Upgrade: 'HTTP' }), request(undefined, { 'Sec-WebSocket-Protocol': 'text' }),
    new Request(request().url.replace('https:', 'http:'), request()), new Request(request().url, { method: 'POST' })])
    assert.equal(destination(req, token), null);
});
test('bad configuration and requests cannot open any socket', async () => {
  const f = fixture();
  assert.equal((await f.handler(request(), {})).status, 503);
  assert.equal((await f.handler(request('/wrong'), env)).status, 404);
  assert.equal(f.dials, 0);
});
test('connect rejection and deadline close sockets and release capacity', async () => {
  for (const opened of [Promise.reject(new Error('Connect failed')), new Promise(() => {})]) {
    const f = fixture({ opened, limits: { connect: 10, sessions: 1 } });
    assert.equal((await f.handler(request(), env)).status, 502);
    assert.equal(f.closed, 1);
    assert.equal((await f.handler(request(), env)).status, 502);
  }
});
test('isolate capacity counts pending connect, not only accepted WebSockets', async () => {
  const f = fixture({ opened: new Promise(() => {}), limits: { connect: 20, sessions: 1 } });
  const first = f.handler(request(), env);
  assert.equal((await f.handler(request(), env)).status, 503);
  assert.equal(f.dials, 1);
  await first;
});
test('default capacity admits six pending sessions but rejects seventh', async () => {
  assert.equal(defaults.sessions, 6);
  const f = fixture({ opened: new Promise(() => {}), limits: { connect: 30 } });
  const pending = Array.from({ length: defaults.sessions }, () => f.handler(request(), env));
  assert.equal((await f.handler(request(), env)).status, 503);
  assert.equal(f.dials, 6);
  assert.deepEqual((await Promise.all(pending)).map(response => response.status), Array(6).fill(502));
  // All expired connection attempts have released their isolate capacity.
  assert.equal((await f.handler(request(), env)).status, 502);
  assert.equal(f.dials, 7);
});
test('socket close rejection before opened is observed and cancels setup', async () => {
  for (const opened of [new Promise(()=>{}),Promise.resolve()]) {
    const f=fixture({opened,socketClosed:Promise.reject(new Error('Early close'))});
    assert.equal((await f.handler(request(),env)).status,502);
    assert.equal(f.closed,1);assert.equal(f.server.code,null);
  }
});
test('serialized writes retain initialization and packet order', async () => {
  let release;
  const f = fixture({ write: () => new Promise(resolve => { release = resolve; }) });
  assert.equal((await f.handler(request(), env)).status, 101);
  f.server.emit('message', new Uint8Array(64).fill(1).buffer);
  f.server.emit('message', new Uint8Array([2, 3]).buffer);
  await until(() => f.writes.length === 1);
  assert.deepEqual(f.writes[0], new Uint8Array(64).fill(1));
  release();
  await until(() => f.writes.length === 2);
  assert.deepEqual(f.writes[1], new Uint8Array([2, 3]));
  release(); f.server.emit('close');
  assert.equal(f.closed, 1);
});
test('text, bad initialization and oversize binary messages close predictably', async () => {
  for (const [data, code] of [['hello',1003],[new ArrayBuffer(63),1002],[new ArrayBuffer(4*1024*1024+5),1009]]) {
    const f = fixture(); await f.handler(request(), env); f.server.emit('message',data);
    assert.equal(f.server.code,code); assert.equal(f.closed,1); assert.equal(f.writes.length,0);
    assert.equal(f.server.closeReason,code===1003?'BAD_FRAME':code===1002?'BAD_INIT':'FRAME_LIMIT');
  }
});
test('queued bytes and queued message count bound a stalled writer', async () => {
  for (const limits of [{ queue: 65 }, { messages: 1 }]) {
    const f=fixture({write:()=>new Promise(()=>{}),limits}); await f.handler(request(),env);
    f.server.emit('message',new ArrayBuffer(64)); f.server.emit('message',new ArrayBuffer(2));
    assert.equal(f.server.code,1009); assert.equal(f.closed,1);
    assert.equal(f.server.closeReason,limits.queue===65?'QUEUE_LIMIT':'MESSAGE_LIMIT');
  }
});
test('init and write deadlines end an abandoned or stalled connection', async () => {
  for (const writing of [false,true]) {
    const f=fixture({write:()=>new Promise(()=>{}),limits:{init:10,write:10}});
    await f.handler(request(),env);
    if(writing)f.server.emit('message',new ArrayBuffer(64));
    await until(()=>f.closed===1);
    assert.equal(f.server.code,writing?1011:1002);
    assert.equal(f.server.closeReason,writing?'WRITE_TIMEOUT':'INIT_TIMEOUT');
  }
});
test('idle deadline and client error release resources once', async () => {
  const f=fixture({limits:{idle:15}});await f.handler(request(),env);
  f.server.emit('message',new ArrayBuffer(64));
  await until(()=>f.closed===1);
  f.server.emit('error');f.server.emit('close');assert.equal(f.closed,1);
});
test('downstream EOF and errors propagate to WebSocket', async () => {
  for(const error of [false,true]){
    const f=fixture();await f.handler(request(),env);
    if(error)f.input.error(new Error('Read failed'));else f.input.close();
    await until(()=>f.closed===1);assert.equal(f.server.code,error?1011:1000);
  }
});
test('total byte budget bounds platform WebSocket output buffering', async () => {
  const f=fixture({limits:{bytes:70}});await f.handler(request(),env);
  f.server.emit('message',new ArrayBuffer(64));
  f.input.enqueue(new Uint8Array(6));await until(()=>f.server.sent.length===1);
  f.input.enqueue(new Uint8Array(1));await until(()=>f.closed===1);
  assert.equal(f.server.code,1008);assert.equal(f.server.closeReason,'BYTE_LIMIT');assert.equal(f.server.sent.length,1);
});
test('closing WebSocket keeps its capacity slot until platform close event', async () => {
  const f=fixture({limits:{sessions:1}});await f.handler(request(),env);
  f.server.close=code=>{f.server.code=code;};
  f.server.emit('message','invalid');
  assert.equal((await f.handler(request(),env)).status,503);
  assert.equal(f.dials,1);
  f.server.emit('close');
  await f.handler(request(),env);
  assert.equal(f.dials,2);
});
test('real local TCP forwards initialization and binary reply through platform adapter', async () => {
  let target, peer;
  const received=[];
  const listener=net.createServer(socket=>{peer=socket;socket.on('data',data=>{received.push(data);socket.write(data);});});
  await new Promise(resolve=>listener.listen(0,'127.0.0.1',resolve));
  const server=new WebSocketStub();
  const handler=createHandler({dial(address){
    target=address;const tcp=net.connect(listener.address().port,'127.0.0.1');
    const streams=Duplex.toWeb(tcp);
    return { ...streams,opened:new Promise((resolve,reject)=>{tcp.once('connect',resolve);tcp.once('error',reject);}),
      closed:new Promise((resolve,reject)=>{tcp.once('close',resolve);tcp.once('error',reject);}),close(){tcp.destroy();} };
  },pair:()=>[null,server],upgrade:()=>({status:101}),limits:{...defaults,idle:1000}});
  try{
    assert.equal((await handler(request(),env)).status,101);
    server.emit('message',new Uint8Array(64).fill(7).buffer);
    server.emit('message',new Uint8Array([9,8,7]).buffer);
    await until(()=>server.sent.reduce((n,x)=>n+x.length,0)===67);
    assert.equal(target,routes[2]);assert.deepEqual(Buffer.concat(received),Buffer.from([...new Uint8Array(64).fill(7),9,8,7]));
    assert.deepEqual(Buffer.concat(server.sent),Buffer.concat(received));
  }finally{server.emit('close');peer?.destroy();await new Promise(resolve=>listener.close(resolve));}
});

test('media upload keeps binary payloads ordered while under the configured budget', async () => {
  const f = fixture({ limits: { bytes: 24 * 1024 * 1024, idle: 2000, write: 1000 } });
  assert.equal((await f.handler(request(), env)).status, 101);
  f.server.emit('message', new Uint8Array(64).fill(1).buffer);
  await until(() => f.writes.length === 1);
  const chunk = new Uint8Array(1024 * 1024).fill(42);
  for (let i = 0; i < 18; i++) {
    // Flush between frames: the Worker queue bound must remain in force.
    f.server.emit('message', chunk.buffer);
    await until(() => f.writes.length === i + 2);
  }
  assert.equal(f.server.code, null);
  assert.equal(f.writes.length, 19);
  assert.equal(f.writes.slice(1).every(x => x.length === chunk.length && x[0] === 42), true);
  f.server.emit('close');
});

test('byte-budget limit returns a non-sensitive reason on sustained media upload', async () => {
  const f = fixture({ limits: { bytes: 2 * 1024 * 1024 + 64 } });
  await f.handler(request(), env);
  f.server.emit('message', new Uint8Array(64).buffer);
  await until(() => f.writes.length === 1);
  const part = new Uint8Array(1024 * 1024);
  for (let i = 0; i < 2; i++) {
    f.server.emit('message', part.buffer);
    await until(() => f.writes.length === i + 2);
  }
  f.server.emit('message', new Uint8Array(1).buffer);
  await until(() => f.closed === 1);
  assert.equal(f.server.code, 1008);
  assert.equal(f.server.closeReason, 'BYTE_LIMIT');
});
