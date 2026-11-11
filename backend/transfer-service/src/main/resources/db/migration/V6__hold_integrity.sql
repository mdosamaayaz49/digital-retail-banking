CREATE FUNCTION check_hold_account_total() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (SELECT held FROM bank_accounts WHERE id=NEW.account_id) <> (SELECT COALESCE(sum(amount),0) FROM account_holds WHERE account_id=NEW.account_id AND released_at IS NULL)
 THEN RAISE EXCEPTION 'Hold record disagrees with available balance'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER hold_account_reconciles AFTER INSERT OR UPDATE ON account_holds DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_hold_account_total();
CREATE TRIGGER immutable_hold_fields BEFORE UPDATE OF account_id,amount,reason,customer_id,idempotency_key OR DELETE ON account_holds FOR EACH ROW EXECUTE FUNCTION prevent_history_mutation();
