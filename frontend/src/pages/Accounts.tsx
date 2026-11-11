import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowUpRight,
  ChevronDown,
  ShieldCheck,
} from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, money } from "../lib/banking";

import {
  AccountCard,
  Badge,
  Empty,
  ErrorBox,
  Title,
} from "../components/banking";
import { useAccounts, useBank } from "../lib/context";
import { History } from "./Transactions";
export function Accounts() {
  const { data = [], isLoading, error } = useAccounts();
  const [filter, setFilter] = useState("ALL");
  const { live, notify } = useBank();
  const query = useQueryClient();
  const [opening, setOpening] = useState(false),
    [name, setName] = useState(""),
    [type, setType] = useState("SAVINGS"),
    [failure, setFailure] = useState<unknown>(null),
    [busy, setBusy] = useState(false);
  async function open(e: React.FormEvent) {
    e.preventDefault();
    if (!live) {
      notify("Sign in to open a fictional account.");
      return;
    }
    setBusy(true);
    try {
      await api("/accounts", {
        method: "POST",
        body: JSON.stringify({ name, type }),
      });
      await query.invalidateQueries({ queryKey: ["accounts"] });
      setOpening(false);
      setName("");
      notify("Your new zero-balance account is ready.");
    } catch (e) {
      setFailure(e);
    } finally {
      setBusy(false);
    }
  }
  const filtered = data.filter(
    (a) => filter === "ALL" || a.type === filter || a.status === filter,
  );
  return (
    <>
      <Title
        eyebrow="YOUR EVERYDAY ESSENTIALS"
        title="My accounts"
        description="A place for today. A little room for tomorrow."
      >
        <button className="btn primary" onClick={() => setOpening(!opening)}>
          Open account
        </button>
        <label className="select-wrap">
          <select
            aria-label="Filter accounts"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="ALL">All accounts</option>
            <option value="SAVINGS">Savings</option>
            <option value="CURRENT">Current</option>
            <option value="ACTIVE">Active</option>
            <option value="FROZEN">Frozen</option>
            <option value="CLOSED">Closed</option>
          </select>
          <ChevronDown size={13} />
        </label>
      </Title>
      <ErrorBox error={error || failure} />
      {opening && (
        <section className="panel form-panel space-bottom">
          <h2>Open a fictional account</h2>
          <form onSubmit={open}>
            <label>
              Account name
              <input
                required
                maxLength={100}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label>
              Account type
              <select value={type} onChange={(e) => setType(e.target.value)}>
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
              </select>
            </label>
            <p className="field-hint">
              Requires verified simulated KYC. New accounts start at RM 0.00. No
              funds are created.
            </p>
            <button className="btn primary" disabled={busy}>
              {busy ? "Opening…" : "Create account"}
            </button>
          </form>
        </section>
      )}
      {isLoading ? (
        <p>Loading accounts…</p>
      ) : filtered.length ? (
        <div className="account-cards">
          {filtered.map((a, i) => (
            <AccountCard key={a.id} account={a} index={i} />
          ))}
        </div>
      ) : (
        <Empty>
          No accounts match this view. Demo funds are seeded for Amira and
          Daniel only.
        </Empty>
      )}
      <div className="notice space-top">
        <ShieldCheck size={20} />
        <span>
          Savings and current accounts hold simulated MYR only. No real money
          moves through this application.
        </span>
      </div>
    </>
  );
}
export function AccountDetails() {
  const { id } = useParams();
  const { data = [] } = useAccounts();
  const account = data.find((a) => a.id === id);
  const { live, notify } = useBank();
  const query = useQueryClient();
  const [failure, setFailure] = useState<unknown>(null);
  const holds = useQuery({
    queryKey: ["holds", id, live],
    queryFn: () =>
      api<
        {
          id: string;
          amount: number;
          reason: string;
          released_at: string | null;
        }[]
      >("/accounts/" + id + "/holds"),
    enabled: live && !!account,
  });
  async function close() {
    if (!live) {
      notify("Sign in to manage this account.");
      return;
    }
    if (
      !window.confirm(
        "Close this zero-balance fictional account permanently? Its history will be retained.",
      )
    )
      return;
    try {
      await api("/accounts/" + id + "/close", { method: "POST" });
      await query.invalidateQueries();
      notify("Account closed. Its history is retained.");
    } catch (e) {
      setFailure(e);
    }
  }
  return (
    <>
      <Link className="back-link" to="/accounts">
        <ArrowLeft size={15} />
        All accounts
      </Link>
      <Title
        title={account?.name || "Account details"}
        description={account?.accountNumber || "Your fictional account"}
      />
      {account ? (
        <>
          <div className="detail-balance panel">
            <p>AVAILABLE BALANCE</p>
            <h2>RM {money(account.availableBalance)}</h2>
            <Badge status={account.status} />
            <Link className="btn primary" to="/transfer">
              Transfer money <ArrowUpRight size={17} />
            </Link>
          </div>
          <ErrorBox error={failure || holds.error} />
          <section className="panel form-panel space-bottom">
            <h2>Account reservations</h2>
            <p>
              Book balance: RM {money(account.balance)} · Held: RM{" "}
              {money(account.balance - account.availableBalance)}
            </p>
            {holds.data
              ?.filter((h) => !h.released_at)
              .map((h) => (
                <div className="statement-row" key={h.id}>
                  <span>{h.reason}</span>
                  <strong>RM {money(h.amount)}</strong>
                </div>
              ))}
            {!holds.data?.some((h) => !h.released_at) && (
              <p>No active holds.</p>
            )}
            {account.status === "ACTIVE" && (
              <button className="btn secondary" onClick={() => void close()}>
                Close empty account
              </button>
            )}
          </section>
          <History accountId={id} />
        </>
      ) : (
        <Empty>Account unavailable.</Empty>
      )}
    </>
  );
}
