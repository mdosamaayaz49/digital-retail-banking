-- Expected: zero rows in both queries. Read-only; safe for simulated balances.
SELECT a.id, a.account_number, a.balance, COALESCE(sum(l.amount),0) AS ledger_balance
FROM bank_accounts a LEFT JOIN ledger_entries l ON l.account_id=a.id
GROUP BY a.id HAVING a.balance <> COALESCE(sum(l.amount),0);
SELECT transfer_id,count(*) AS postings,sum(amount) AS net
FROM ledger_entries GROUP BY transfer_id HAVING count(*)<>2 OR sum(amount)<>0;
