import { readFileSync } from 'node:fs';
import { createRelay } from './server.mjs';
import { configuration } from './config.mjs';

// TLS private key and access token remain on the relay host, outside Git.
try {
  const { host, port, options } = configuration(process.env);
  const relay = createRelay({ ...options,
    tls: { key: readFileSync(process.env.TGWS_TLS_KEY), cert: readFileSync(process.env.TGWS_TLS_CERT) },
  });
  relay.server.once('error', () => { console.error('TGWS relay listener failed'); process.exitCode = 1; void relay.close(); });
  relay.server.listen(port, host, () => console.info('TGWS WSS relay listening'));
  for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => { void relay.close(); });
} catch {
  console.error('TGWS relay configuration invalid; check token, TLS files and bounded environment options');
  process.exitCode = 1;
}
