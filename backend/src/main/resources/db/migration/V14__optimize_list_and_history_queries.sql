-- Coordinate V14 with the team before shared rollout.
-- Existing unique/FK-leading indexes already cover email, roles, booking ownership,
-- lifecycle lookup, tokens and worker claims. Add only missing query access paths.
CREATE INDEX ix_users_created_id ON swiftdrop.users (created_at DESC, id);

-- Dispatch board ordering, including filtered unassigned and overdue views.
CREATE INDEX ix_lifecycles_updated_id ON swiftdrop.parcel_lifecycles (updated_at DESC, id DESC);
CREATE INDEX ix_lifecycles_unassigned_updated ON swiftdrop.parcel_lifecycles (updated_at DESC, id DESC)
    WHERE assigned_courier_id IS NULL AND status = 'CREATED';
CREATE INDEX ix_lifecycles_open_updated ON swiftdrop.parcel_lifecycles (updated_at, id)
    WHERE status NOT IN ('DELIVERED', 'FAILED');

-- The existing active-assignment indexes omit revoked assignment history.
CREATE INDEX ix_assignments_booking_time ON swiftdrop.parcel_assignments (booking_id, assigned_at);
CREATE INDEX ix_assignments_courier_time ON swiftdrop.parcel_assignments (courier_id, assigned_at);

-- Daily completion reports and the latest 100 notification jobs.
CREATE INDEX ix_status_history_completion_time ON swiftdrop.parcel_status_history (occurred_at)
    WHERE new_status IN ('DELIVERED', 'FAILED');
CREATE INDEX ix_notification_jobs_created ON swiftdrop.notification_jobs (created_at DESC);
