import { jsonRequest } from '../identity/apiClient.js';

export function loadPublicTracking(trackingNumber, fetchImpl = globalThis.fetch) {
  return jsonRequest(`/api/v1/tracking/${encodeURIComponent(trackingNumber)}`, {}, fetchImpl);
}

export function trackingEventsUrl(trackingNumber) {
  return `/api/v1/tracking/${encodeURIComponent(trackingNumber)}/events`;
}
export function acceptTrackingSnapshot(current, next) {
  if (!current || current.trackingNumber !== next.trackingNumber) return next;
  const sequence = snapshot => snapshot.timeline?.at(-1)?.sequenceNo ?? 0;
  return sequence(next) >= sequence(current) ? next : current;
}
