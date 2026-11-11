-- Defense in depth: posting metadata and cached balances must agree at commit.
CREATE OR REPLACE FUNCTION check_balanced_postings() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t transfers%ROWTYPE;
BEGIN
  SELECT * INTO t FROM transfers WHERE id=NEW.transfer_id;
  IF (SELECT count(*) FROM ledger_entries WHERE transfer_id=t.id)<>2
     OR (SELECT sum(amount) FROM ledger_entries WHERE transfer_id=t.id)<>0
     OR NOT EXISTS (SELECT 1 FROM ledger_entries WHERE transfer_id=t.id AND account_id=t.source_id AND amount=-t.amount)
     OR NOT EXISTS (SELECT 1 FROM ledger_entries WHERE transfer_id=t.id AND account_id=t.destination_id AND amount=t.amount)
  THEN RAISE EXCEPTION 'Ledger entries disagree with transfer'; END IF;
  RETURN NEW;
END $$;
CREATE FUNCTION check_account_reconciles() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (SELECT balance FROM bank_accounts WHERE id=NEW.id)
     <> (SELECT COALESCE(sum(amount),0) FROM ledger_entries WHERE account_id=NEW.id)
  THEN RAISE EXCEPTION 'Account balance disagrees with ledger'; END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER account_reconciles AFTER INSERT OR UPDATE ON bank_accounts
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_account_reconciles();
CREATE FUNCTION check_transfer_posted() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (SELECT count(*) FROM ledger_entries WHERE transfer_id=NEW.id)<>2
  THEN RAISE EXCEPTION 'A settled transfer requires two postings'; END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER transfer_has_postings AFTER INSERT ON transfers
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_transfer_posted();
CREATE TRIGGER immutable_transfer_fields
  BEFORE UPDATE OF source_id,destination_id,amount,currency,reference,customer_id,idempotency_key,request_hash,reversal_of,created_at OR DELETE
  ON transfers FOR EACH ROW EXECUTE FUNCTION prevent_history_mutation();
