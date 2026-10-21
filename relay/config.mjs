/** Bounded host configuration. Never print this object: it contains the access token. */
export function configuration(env) {
  const integer = (name, fallback, min, max) => {
    const text = env[name] ?? String(fallback);
    if (!/^\d+$/.test(text)) throw new Error('Invalid relay environment');
    const value = Number(text);
    if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error('Invalid relay environment');
    return value;
  };
  const host = env.TGWS_LISTEN_HOST ?? '0.0.0.0';
  if (!['0.0.0.0', '127.0.0.1', '::', '::1'].includes(host)) throw new Error('Invalid relay bind address');
  return { host, port: integer('TGWS_LISTEN_PORT', 443, 1, 65535), options: {
    token: env.TGWS_RELAY_TOKEN,
    maxSessions: integer('TGWS_MAX_SESSIONS', 8, 1, 64),
    connectMillis: integer('TGWS_CONNECT_MS', 8000, 1000, 15000),
    idleMillis: integer('TGWS_IDLE_MS', 300000, 30000, 3600000),
    writeMillis: integer('TGWS_WRITE_MS', 10000, 1000, 30000),
  } };
}
