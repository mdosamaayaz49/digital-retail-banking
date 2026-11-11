ALTER TABLE account_holds ADD COLUMN customer_id UUID REFERENCES customers(id);
ALTER TABLE account_holds ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX holds_customer_key ON account_holds(customer_id,idempotency_key);
CREATE TABLE payment_orders (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id),
 source_id UUID NOT NULL REFERENCES bank_accounts(id), destination_id UUID NOT NULL REFERENCES bank_accounts(id),
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0 AND amount<=10000), description VARCHAR(140) NOT NULL,
 channel VARCHAR(20) NOT NULL CHECK(channel IN ('SCHEDULED','DUITNOW_MOCK')),
 alias VARCHAR(120), expected_outcome VARCHAR(12) NOT NULL DEFAULT 'ACCEPT',
 status VARCHAR(15) NOT NULL CHECK(status IN ('PENDING','PROCESSING','SETTLED','CANCELLED','REJECTED')),
 scheduled_at TIMESTAMPTZ NOT NULL, hold_id UUID NOT NULL UNIQUE REFERENCES account_holds(id),
 transfer_id UUID UNIQUE REFERENCES transfers(id), idempotency_key UUID NOT NULL, request_hash VARCHAR(64) NOT NULL,
 network_reference VARCHAR(40) UNIQUE, failure_code VARCHAR(60), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(customer_id,idempotency_key), CHECK(source_id<>destination_id)
);
CREATE INDEX orders_due ON payment_orders(scheduled_at) WHERE status='PENDING';
CREATE TABLE mock_payment_aliases(alias VARCHAR(120) PRIMARY KEY, account_id UUID NOT NULL REFERENCES bank_accounts(id), display_name VARCHAR(100) NOT NULL, outcome VARCHAR(12) NOT NULL CHECK(outcome IN ('ACCEPT','REJECT')));
INSERT INTO mock_payment_aliases VALUES
 ('daniel@example.test','10000000-0000-0000-0000-000000000003','Daniel Tan','ACCEPT'),
 ('reject@example.test','10000000-0000-0000-0000-000000000003','Network rejection demo','REJECT');
CREATE TABLE network_messages(order_id UUID PRIMARY KEY REFERENCES payment_orders(id), reference VARCHAR(40) NOT NULL UNIQUE, outcome VARCHAR(15) NOT NULL CHECK(outcome IN ('ACCEPTED','REJECTED')), transfer_id UUID REFERENCES transfers(id), amount NUMERIC(19,2) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE rejected_requests(id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id), idempotency_key UUID NOT NULL, code VARCHAR(60) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(customer_id,idempotency_key,code));
CREATE TABLE notification_dead_letters(id BIGSERIAL PRIMARY KEY, topic VARCHAR(100) NOT NULL, partition_id INT NOT NULL, offset_id BIGINT NOT NULL, payload TEXT NOT NULL, error_type VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(topic,partition_id,offset_id));
CREATE FUNCTION check_held_reconciles() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (SELECT held FROM bank_accounts WHERE id=NEW.id) <> (SELECT COALESCE(sum(amount),0) FROM account_holds WHERE account_id=NEW.id AND released_at IS NULL)
 THEN RAISE EXCEPTION 'Available balance reservations disagree with holds'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER held_reconciles AFTER INSERT OR UPDATE ON bank_accounts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_held_reconciles();
