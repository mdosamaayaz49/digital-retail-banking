import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Clock, ShieldCheck, X } from "lucide-react";
import { useState } from "react";
import { Badge, Empty, ErrorBox, Title } from "../components/banking";
import {
  api,
  date,
  money,
  previewBeneficiaries,
  type Beneficiary,
  type Page,
} from "../lib/banking";
import { useAccounts, useBank } from "../lib/context";
type Order = {
  id: string;
  amount: number;
  description: string;
  channel: string;
  status: string;
  scheduled_at: string;
  network_reference: string | null;
  failure_code: string | null;
  transfer_id: string | null;
};
type Draft = {
  sourceId: string;
  destinationId: string | null;
  amount: string;
  description: string;
  channel: string;
  alias: string | null;
  scheduledAt: string;
};
export function Payments() {
  const { live, notify } = useBank();
  const { data: accounts = [] } = useAccounts();
  const query = useQueryClient();
  const [channel, setChannel] = useState("DUITNOW_MOCK"),
    [source, setSource] = useState(""),
    [destination, setDestination] = useState(""),
    [amount, setAmount] = useState(""),
    [description, setDescription] = useState(""),
    [alias, setAlias] = useState("daniel@example.test"),
    [schedule, setSchedule] = useState(""),
    [review, setReview] = useState<Draft | null>(null),
    [attempt, setAttempt] = useState({ body: "", key: "" }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [page, setPage] = useState(0);
  const beneficiaries = useQuery({
    queryKey: ["beneficiaries", live],
    queryFn: () =>
      live
        ? api<Beneficiary[]>("/beneficiaries")
        : Promise.resolve(previewBeneficiaries),
  });
  const orders = useQuery({
    queryKey: ["orders", live, page],
    queryFn: () =>
      live
        ? api<Page<Order>>("/payment-orders?page=" + page + "&size=10")
        : Promise.resolve({ items: [], total: 0, page: 0, size: 10 }),
    refetchInterval: live ? 3000 : false,
  });
  function prepare(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (
      !/^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(amount) ||
      Number(amount) <= 0 ||
      Number(amount) > 10000
    ) {
      setError(
        new Error("Enter RM 0.01 to RM 10,000.00 with at most two decimals."),
      );
      return;
    }
    setReview({
      sourceId: source,
      destinationId: channel === "SCHEDULED" ? destination : null,
      amount,
      description: description.trim(),
      channel,
      alias: channel === "DUITNOW_MOCK" ? alias.trim() : null,
      scheduledAt:
        channel === "SCHEDULED"
          ? new Date(schedule).toISOString()
          : new Date().toISOString(),
    });
  }
  async function confirm() {
    if (!review) return;
    if (!live) {
      notify("Sign in to reserve and settle simulated payments.");
      setReview(null);
      return;
    }
    const body = JSON.stringify(review),
      key = attempt.body === body ? attempt.key : crypto.randomUUID();
    setAttempt({ body, key });
    setBusy(true);
    setError(null);
    try {
      await api("/payment-orders", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body,
      });
      setReview(null);
      setAttempt({ body: "", key: "" });
      setAmount("");
      setDescription("");
      await query.invalidateQueries();
      notify("Payment reserved. You can cancel it until settlement begins.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function cancel(id: string) {
    if (
      !window.confirm(
        "Cancel this pending payment and release its reserved funds?",
      )
    )
      return;
    try {
      await api("/payment-orders/" + id + "/cancel", { method: "POST" });
      await query.invalidateQueries();
      notify("Payment cancelled. Reserved funds are available again.");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <>
      <Title
        eyebrow="PLAN AHEAD · SIMULATED NETWORK"
        title="Payments & schedules"
        description="Reserve a future transfer or try a fictional DuitNow-style payment."
      />
      <div className="notice">
        <ShieldCheck size={20} />
        Mock network only. No connection to DuitNow, PayNet or a real bank.
      </div>
      <div className="form-layout">
        <section className="panel form-panel">
          <div className="form-tabs">
            <button
              className={channel === "DUITNOW_MOCK" ? "active" : ""}
              onClick={() => setChannel("DUITNOW_MOCK")}
            >
              Mock instant payment
            </button>
            <button
              className={channel === "SCHEDULED" ? "active" : ""}
              onClick={() => setChannel("SCHEDULED")}
            >
              Schedule a transfer
            </button>
          </div>
          <form onSubmit={prepare}>
            <label>
              From account
              <select
                required
                value={source}
                onChange={(e) => setSource(e.target.value)}
              >
                <option value="">Select account</option>
                {accounts
                  .filter((a) => a.status === "ACTIVE")
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name} · RM {money(a.availableBalance)}
                    </option>
                  ))}
              </select>
            </label>
            {channel === "DUITNOW_MOCK" ? (
              <label>
                Fictional payment alias
                <select
                  value={alias}
                  onChange={(e) => setAlias(e.target.value)}
                >
                  <option value="daniel@example.test">
                    daniel@example.test · accepted
                  </option>
                  <option value="reject@example.test">
                    reject@example.test · rejected
                  </option>
                </select>
                <small className="field-hint">
                  Register Daniel as a beneficiary first. Both test aliases
                  resolve to his fictional account.
                </small>
              </label>
            ) : (
              <>
                <label>
                  Destination account
                  <select
                    required
                    value={destination}
                    onChange={(e) => setDestination(e.target.value)}
                  >
                    <option value="">Select destination</option>
                    {accounts
                      .filter((a) => a.id !== source && a.status === "ACTIVE")
                      .map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                    {beneficiaries.data?.map((b) => (
                      <option key={b.id} value={b.account_id}>
                        {b.nickname}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Settlement time (your local time)
                  <input
                    required
                    type="datetime-local"
                    value={schedule}
                    onChange={(e) => setSchedule(e.target.value)}
                  />
                </label>
                <small className="field-hint">
                  Up to 90 days ahead. Funds are reserved now; settlement
                  follows Malaysian calendar-day limits.
                </small>
              </>
            )}
            <label>
              Amount (MYR)
              <input
                required
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
              />
            </label>
            <label>
              Payment reference
              <input
                required
                maxLength={140}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <ErrorBox error={error} />
            <button className="btn primary full">
              Review payment <ArrowRight size={16} />
            </button>
          </form>
        </section>
        <aside className="transfer-aside">
          <Clock size={32} />
          <h2>A clear path for every payment.</h2>
          <p>
            A pending payment reduces available funds without changing your book
            balance. Cancellation releases the hold. Accepted payments settle
            through the same atomic double-entry ledger.
          </p>
          <p>
            Rejected mock payments release every reserved sen and generate a
            failure notification. Their network references remain visible for
            reconciliation.
          </p>
        </aside>
      </div>
      <section className="panel space-top">
        <div className="panel-title">
          <h2>Your payment orders</h2>
          <span>{orders.data?.total || 0} orders</span>
        </div>
        <ErrorBox error={orders.error} />
        {orders.isLoading ? (
          <p className="loading">Loading payment orders…</p>
        ) : !orders.data?.items.length ? (
          <Empty>No payment orders yet.</Empty>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Payment</th>
                  <th>Settlement date</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Reference</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {orders.data?.items.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <strong>{o.description}</strong>
                      <small className="review-flag">
                        {o.channel === "DUITNOW_MOCK"
                          ? "Mock DuitNow-style"
                          : "Scheduled transfer"}
                      </small>
                    </td>
                    <td>
                      {date(o.scheduled_at)}
                      <small className="review-flag">
                        {new Date(o.scheduled_at).toLocaleTimeString("en-MY", {
                          timeZone: "Asia/Kuala_Lumpur",
                          hour: "2-digit",
                          minute: "2-digit",
                        })}{" "}
                        MYT
                      </small>
                    </td>
                    <td>RM {money(o.amount)}</td>
                    <td>
                      <Badge status={o.status} />
                      {o.failure_code && (
                        <small className="review-flag">{o.failure_code}</small>
                      )}
                    </td>
                    <td className="mono">
                      {o.network_reference || "Awaiting settlement"}
                    </td>
                    <td>
                      {o.status === "PENDING" && (
                        <button
                          className="btn mini secondary"
                          onClick={() => void cancel(o.id)}
                        >
                          Cancel
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="pagination">
          <span>Page {page + 1}</span>
          <button disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <button
            disabled={(page + 1) * 10 >= (orders.data?.total || 0)}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
      {review && (
        <div className="modal-backdrop">
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="payment-review"
          >
            <button
              className="icon-btn close"
              aria-label="Close payment review"
              disabled={busy}
              onClick={() => setReview(null)}
            >
              <X size={18} />
            </button>
            <h2 id="payment-review">Review your payment</h2>
            <div className="review-amount">RM {money(review.amount)}</div>
            <dl>
              <dt>From</dt>
              <dd>{accounts.find((a) => a.id === review.sourceId)?.name}</dd>
              <dt>Destination</dt>
              <dd>
                {review.alias ||
                  accounts.find((a) => a.id === review.destinationId)?.name ||
                  beneficiaries.data?.find(
                    (b) => b.account_id === review.destinationId,
                  )?.nickname}
              </dd>
              <dt>Reference</dt>
              <dd>{review.description}</dd>
              <dt>Scheduled</dt>
              <dd>
                {date(review.scheduledAt)}{" "}
                {new Date(review.scheduledAt).toLocaleTimeString("en-MY", {
                  timeZone: "Asia/Kuala_Lumpur",
                  hour: "2-digit",
                  minute: "2-digit",
                })}{" "}
                MYT
              </dd>
            </dl>
            <ErrorBox error={error} />
            <button
              autoFocus
              className="btn primary full"
              disabled={busy}
              onClick={() => void confirm()}
            >
              {busy ? "Reserving funds…" : "Confirm payment"}
            </button>
            <button
              className="btn text full"
              disabled={busy}
              onClick={() => setReview(null)}
            >
              Go back
            </button>
          </section>
        </div>
      )}
    </>
  );
}
