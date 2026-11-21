import { it,expect } from 'vitest';
import { acceptTrackingSnapshot } from './trackingApi.js';
it('does not regress live status when a delayed reconnect snapshot arrives',()=>{
  const delivered={trackingNumber:'SDP-ABC12345',currentStatus:'DELIVERED',timeline:[{sequenceNo:5}]};
  const older={...delivered,currentStatus:'IN_TRANSIT',timeline:[{sequenceNo:3}]};
  expect(acceptTrackingSnapshot(delivered,older)).toBe(delivered);
  expect(acceptTrackingSnapshot(older,delivered)).toBe(delivered);
});
it('accepts a different parcel and allows a canonical correction at the same sequence',()=>{
  const old={trackingNumber:'SDP-ABC12345',timeline:[{sequenceNo:5}]};
  const different={trackingNumber:'SDP-ZYX98765',timeline:[{sequenceNo:1}]};
  expect(acceptTrackingSnapshot(old,different)).toBe(different);
  const corrected={...old,currentStatus:'DELIVERED'}; expect(acceptTrackingSnapshot(old,corrected)).toBe(corrected);
});
