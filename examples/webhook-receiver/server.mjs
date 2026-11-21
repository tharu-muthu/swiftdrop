import { createServer } from 'node:http';
import { createHmac, timingSafeEqual, createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export function verify(secret, timestamp, body, signature, now = Date.now()) {
  if (!/^\d{1,12}$/.test(timestamp || '') || Math.abs(now / 1000 - Number(timestamp)) > 300) return false;
  const key = Buffer.from(secret, 'base64');
  if (key.length < 32) return false;
  const expected = Buffer.from(`v1=${createHmac('sha256', key).update(`${timestamp}.`).update(body).digest('base64')}`);
  const received = Buffer.from(signature || '');
  return expected.length === received.length && timingSafeEqual(expected, received);
}

export function receiver(secret) {
  const seen = new Map(); // Demo only. Production receivers persist event IDs with their business changes.
  return createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    if (request.method !== 'POST' || request.url !== '/webhooks') { response.writeHead(404).end('{}'); return; }
    const chunks = []; let length = 0;
    for await (const chunk of request) {
      length += chunk.length;
      if (length > 65536) { response.writeHead(413).end('{}'); return; }
      chunks.push(chunk);
    }
    const body = Buffer.concat(chunks);
    if (!verify(secret, request.headers['x-swiftdrop-timestamp'], body, request.headers['x-swiftdrop-signature'])) { response.writeHead(401).end('{}'); return; }
    let event;
    try { event = JSON.parse(body); } catch { response.writeHead(400).end('{}'); return; }
    if (!event.eventId || event.eventId !== request.headers['x-swiftdrop-event-id']) { response.writeHead(400).end('{}'); return; }
    const hash = createHash('sha256').update(body).digest('hex');
    const prior = seen.get(event.eventId);
    if (prior && prior !== hash) { response.writeHead(409).end('{}'); return; }
    if (!prior) {
      if (seen.size >= 10000) seen.delete(seen.keys().next().value);
      seen.set(event.eventId, hash);
      console.log(`Accepted ${event.eventType} ${event.eventId}`);
    }
    response.writeHead(200).end(JSON.stringify({ accepted: true, duplicate: Boolean(prior) }));
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const secret = process.env.WEBHOOK_SIGNING_SECRET;
  if (!secret || Buffer.from(secret, 'base64').length < 32) throw new Error('Set WEBHOOK_SIGNING_SECRET to the subscription signing secret.');
  receiver(secret).listen(Number(process.env.PORT || 8088), '127.0.0.1', () => console.log('Receiver listening on http://127.0.0.1:8088/webhooks'));
}
