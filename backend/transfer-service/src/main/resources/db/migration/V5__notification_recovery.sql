ALTER TABLE notification_dead_letters ADD COLUMN replayed_at TIMESTAMPTZ;
ALTER TABLE notification_dead_letters ADD COLUMN repaired_payload TEXT;
CREATE INDEX dead_letters_pending ON notification_dead_letters(created_at) WHERE replayed_at IS NULL;
