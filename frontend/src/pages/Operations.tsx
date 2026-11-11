import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Empty, ErrorBox } from "../components/banking";
import { api, bankDate, date, money, type Page } from "../lib/banking";
import { useBank } from "../lib/context";
export function Reconciliation() {
  const q = useQuery({
    queryKey: ["reconciliation"],
    queryFn: () =>
      api<{
        balanced: boolean;
        balanceDifferences: unknown[];
        ledgerDifferences: unknown[];
        networkDifferences: unknown[];
        holdDifferences: unknown[];
      }>("/admin/reconciliation"),
  });
  return (
    <section className="panel form-panel">
      <h2>Ledger & network reconciliation</h2>
      <p>
        Every cached balance, double-entry transfer, active reservation and
        mock-network payment must reconcile.
      </p>
      <ErrorBox error={q.error} />
      {q.isLoading ? (
        <p>Checking reconciliation…</p>
      ) : (
        q.data && (
          <>
            <Badge status={q.data.balanced ? "VERIFIED" : "REJECTED"} />
            {Object.entries(q.data)
              .filter(([k]) => k !== "balanced")
              .map(([key, value]) => (
                <div className="statement-row" key={key}>
                  <span>{key.replace("Differences", " differences")}</span>
                  <strong>{Array.isArray(value) ? value.length : 0}</strong>
                </div>
              ))}
            <button
              className="btn secondary space-top"
              onClick={() => void q.refetch()}
            >
              Run reconciliation again
            </button>
          </>
        )
      )}
    </section>
  );
}
export function Holds() {
  const { notify } = useBank();
  const query = useQueryClient();
  const [account, setAccount] = useState(""),
    [amount, setAmount] = useState(""),
    [reason, setReason] = useState(""),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState(false),
    [attempt, setAttempt] = useState({ body: "", key: "" });
  type Account = {
    id: string;
    name: string;
    display_name: string;
    balance: number;
    held: number;
  };
  type Hold = {
    id: string;
    amount: number;
    reason: string;
    released_at: string | null;
  };
  const accounts = useQuery({
    queryKey: ["admin-hold-accounts"],
    queryFn: () => api<Page<Account>>("/admin/accounts?size=100"),
  });
  const holds = useQuery({
    queryKey: ["admin-holds", account],
    queryFn: () => api<Hold[]>("/admin/accounts/" + account + "/holds"),
    enabled: !!account,
  });
  async function place(e: React.FormEvent) {
    e.preventDefault();
    const body = JSON.stringify({ accountId: account, amount, reason }),
      key = attempt.body === body ? attempt.key : crypto.randomUUID();
    setAttempt({ body, key });
    setBusy(true);
    try {
      await api("/admin/holds", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body,
      });
      setAttempt({ body: "", key: "" });
      setAmount("");
      await query.invalidateQueries();
      notify("Hold placed. Available funds were updated.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function release(id: string) {
    try {
      await api("/admin/holds/" + id + "/release", { method: "POST" });
      await query.invalidateQueries();
      notify("Hold released.");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <section className="panel form-panel">
      <h2>Account reservations</h2>
      <form onSubmit={place}>
        <label>
          Fictional customer account
          <select
            required
            value={account}
            onChange={(e) => setAccount(e.target.value)}
          >
            <option value="">Select account</option>
            {accounts.data?.items.map((a) => (
              <option value={a.id} key={a.id}>
                {a.display_name} · {a.name} · available RM{" "}
                {money(Number(a.balance) - Number(a.held))}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hold amount (MYR)
          <input
            required
            inputMode="decimal"
            pattern="(?:0|[1-9][0-9]*)(?:[.][0-9]{1,2})?"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label>
          Reason
          <input
            required
            maxLength={100}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <ErrorBox error={error || accounts.error || holds.error} />
        <button className="btn primary" disabled={busy}>
          Place hold
        </button>
      </form>
      <h2 className="space-top">Existing holds</h2>
      {!holds.data?.length ? (
        <Empty>Select an account to inspect its holds.</Empty>
      ) : (
        holds.data.map((h) => (
          <div className="statement-row" key={h.id}>
            <span>
              <strong>{h.reason}</strong>
              <small>RM {money(h.amount)}</small>
            </span>
            {h.released_at ? (
              <Badge status="CLOSED" />
            ) : (
              <button
                className="btn secondary"
                onClick={() => void release(h.id)}
              >
                Release hold
              </button>
            )}
          </div>
        ))
      )}
    </section>
  );
}
export function Reports() {
  const [from, setFrom] = useState(bankDate().slice(0, 7) + "-01"),
    [to, setTo] = useState(bankDate());
  type Report = {
    summary: Record<string, number>;
    channels: {
      channel: string;
      status: string;
      orders: number;
      amount: number;
    }[];
    note: string;
  };
  const q = useQuery({
    queryKey: ["reports", from, to],
    queryFn: () => api<Report>("/admin/reports?from=" + from + "&to=" + to),
    enabled: !!from && !!to,
  });
  function download() {
    if (!q.data) return;
    const rows = [
      ["Metric", "Value"],
      ...Object.entries(q.data.summary).map(([k, v]) => [k, String(v)]),
      ...q.data.channels.map((c) => [
        c.channel + " " + c.status,
        String(c.amount),
      ]),
    ];
    const url = URL.createObjectURL(
      new Blob([rows.map((r) => r.join(",")).join("\n")], { type: "text/csv" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "Nusantara-simulation-report.csv";
    a.click();
    URL.revokeObjectURL(url);
  }
  return (
    <section className="panel form-panel">
      <h2>Transaction reporting</h2>
      <div className="filter-row space-top">
        <label className="date-filter">
          From
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
          />
        </label>
        <label className="date-filter">
          To
          <input
            type="date"
            value={to}
            min={from}
            onChange={(e) => setTo(e.target.value)}
          />
        </label>
      </div>
      <ErrorBox error={q.error} />
      {q.data && (
        <>
          <div className="summary-grid">
            {Object.entries(q.data.summary).map(([k, v]) => (
              <div className="summary-card panel" key={k}>
                <span>{k.replaceAll("_", " ")}</span>
                <strong>{k.includes("amount") ? "RM " + money(v) : v}</strong>
              </div>
            ))}
          </div>
          <p className="field-hint">{q.data.note}</p>
          <button className="btn secondary space-top" onClick={download}>
            Download report CSV
          </button>
          <div className="table-scroll space-top">
            <table>
              <thead>
                <tr>
                  <th>Channel</th>
                  <th>Status</th>
                  <th>Orders</th>
                  <th>Gross amount</th>
                </tr>
              </thead>
              <tbody>
                {q.data.channels.map((c) => (
                  <tr key={c.channel + c.status}>
                    <td>{c.channel}</td>
                    <td>
                      <Badge status={c.status} />
                    </td>
                    <td>{c.orders}</td>
                    <td>RM {money(c.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
export function NotificationRecovery() {
  const { notify } = useBank();
  const query = useQueryClient();
  type Letter = {
    id: number;
    topic: string;
    error_type: string;
    created_at: string;
    replayed_at: string | null;
  };
  const [selected, setSelected] = useState(""),
    [customer, setCustomer] = useState(""),
    [eventType, setEventType] = useState("TRANSFER_COMPLETED"),
    [message, setMessage] = useState(""),
    [error, setError] = useState<unknown>(null);
  const q = useQuery({
    queryKey: ["dead-letters"],
    queryFn: () =>
      api<Page<Letter>>("/admin/notification-dead-letters?size=100"),
  });
  const customers = useQuery({
    queryKey: ["recovery-customers"],
    queryFn: () =>
      api<Page<{ id: string; display_name: string }>>(
        "/admin/customers?size=100",
      ),
  });
  async function repair(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api("/admin/notification-dead-letters/" + selected + "/retry", {
        method: "POST",
        body: JSON.stringify({ customerId: customer, eventType, message }),
      });
      await query.invalidateQueries();
      notify("Corrected event published. Recovery history was retained.");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <section className="panel form-panel">
      <h2>Notification recovery</h2>
      <p>
        After bounded retries, poison messages are durably quarantined before
        Kafka offsets advance. Repair and republish a record without modifying
        financial history.
      </p>
      <ErrorBox error={q.error || error} />
      {!q.data?.items.length ? (
        <Empty>No quarantined notification records.</Empty>
      ) : (
        <form onSubmit={repair}>
          <label>
            Recovery record
            <select
              required
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
            >
              <option value="">Select record</option>
              {q.data.items
                .filter((d) => !d.replayed_at)
                .map((d) => (
                  <option value={d.id} key={d.id}>
                    #{d.id} · {d.error_type} · {date(d.created_at)}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Fictional recipient
            <select
              required
              value={customer}
              onChange={(e) => setCustomer(e.target.value)}
            >
              <option value="">Select customer</option>
              {customers.data?.items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.display_name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Event type
            <input
              required
              pattern="[A-Z_]{3,60}"
              value={eventType}
              onChange={(e) => setEventType(e.target.value)}
            />
          </label>
          <label>
            Corrected message
            <input
              required
              maxLength={500}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />
          </label>
          <button className="btn primary">Repair & republish</button>
        </form>
      )}
    </section>
  );
}
