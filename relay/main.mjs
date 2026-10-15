import { readFileSync } from 'node:fs';
import { createRelay } from './server.mjs';

// TLS private key and access token stay on the relay host, outside Git.
const relay = createRelay({
  token: process.env.TGWS_RELAY_TOKEN,
  tls: { key: readFileSync(process.env.TGWS_TLS_KEY), cert: readFileSync(process.env.TGWS_TLS_CERT) },
});
relay.server.listen(443, '0.0.0.0', () => console.info('TGWS WSS relay listening'));
for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => { void relay.close(); });
