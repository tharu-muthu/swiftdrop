import { jsonRequest } from '../identity/apiClient.js';

export function loadPublicTracking(trackingNumber, fetchImpl = globalThis.fetch) {
  return jsonRequest(`/api/v1/tracking/${encodeURIComponent(trackingNumber)}`, {}, fetchImpl);
}

export function trackingEventsUrl(trackingNumber) {
  return `/api/v1/tracking/${encodeURIComponent(trackingNumber)}/events`;
}
