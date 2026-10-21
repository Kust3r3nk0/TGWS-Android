import { test } from 'node:test';
import assert from 'node:assert/strict';
import { configuration } from './config.mjs';
test('environment configuration uses bounded defaults and rejects unsafe values', () => {
  const c = configuration({}); assert.equal(c.port, 443); assert.equal(c.options.maxSessions, 8);
  for (const env of [{ TGWS_MAX_SESSIONS: '65' }, { TGWS_WRITE_MS: '0' }, { TGWS_IDLE_MS: 'Infinity' },
    { TGWS_LISTEN_HOST: 'private.example' }, { TGWS_LISTEN_PORT: '443oops' }]) assert.throws(() => configuration(env));
  assert.equal(configuration({ TGWS_MAX_SESSIONS: '4', TGWS_LISTEN_HOST: '::', TGWS_WRITE_MS: '12000' }).options.writeMillis, 12000);
});
