import {
  ArrowLeft,
  ArrowUpRight,
  ChevronDown,
  ShieldCheck,
} from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { money } from "../lib/banking";

import {
  AccountCard,
  Badge,
  Empty,
  ErrorBox,
  Title,
} from "../components/banking";
import { useAccounts } from "../lib/context";
import { History } from "./Transactions";
export function Accounts() {
  const { data = [], isLoading, error } = useAccounts();
  const [filter, setFilter] = useState("ALL");
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
          </select>
          <ChevronDown size={13} />
        </label>
      </Title>
      <ErrorBox error={error} />
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
          <History accountId={id} />
        </>
      ) : (
        <Empty>Account unavailable.</Empty>
      )}
    </>
  );
}
