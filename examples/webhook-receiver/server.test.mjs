import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { verify } from './server.mjs';

test('accepts the exact signed body and rejects tampering and replay outside tolerance', () => {
  const secret = Buffer.alloc(32, 7).toString('base64'), timestamp = '1795260000';
  const body = Buffer.from('{"eventId":"test"}');
  const signature = `v1=${createHmac('sha256', Buffer.from(secret, 'base64')).update(`${timestamp}.`).update(body).digest('base64')}`;
  assert.equal(verify(secret, timestamp, body, signature, Number(timestamp) * 1000), true);
  assert.equal(verify(secret, timestamp, Buffer.from('{}'), signature, Number(timestamp) * 1000), false);
  assert.equal(verify(secret, timestamp, body, signature, (Number(timestamp) + 301) * 1000), false);
  assert.equal(verify(secret, timestamp, body, 'v1=bad', Number(timestamp) * 1000), false);
});
