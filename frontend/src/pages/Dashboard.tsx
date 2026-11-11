import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  ChevronDown,
  Clock,
  Download,
  Eye,
  EyeOff,
  Plus,
  ShieldCheck,
  Users,
} from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  api,
  money,
  previewTransactions,
  type Page,
  type Transaction,
} from "../lib/banking";

import {
  AccountCard,
  ErrorBox,
  Title,
  TransactionTable,
} from "../components/banking";
import { useAccounts, useBank, useProfile } from "../lib/context";
export function Dashboard() {
  const { live } = useBank();
  const { data: accounts = [], isLoading, error } = useAccounts();
  const { data: profile } = useProfile();
  const [visible, setVisible] = useState(true);
  const [period, setPeriod] = useState("This month");
  const history = useQuery({
    queryKey: ["recent", live],
    queryFn: () =>
      live
        ? api<Page<Transaction>>("/transactions?size=5")
        : Promise.resolve({
            items: previewTransactions,
            total: 4,
            page: 0,
            size: 5,
          }),
  });
  const total = accounts.reduce((sum, a) => sum + Number(a.balance), 0);
  const analytics = useQuery({
    queryKey: ["analytics", live],
    queryFn: () =>
      api<{ name: string; period: string; income: number; spending: number }[]>(
        "/analytics?months=6",
      ),
    enabled: live,
  });
  const chart = live
    ? analytics.data || []
    : [
        { name: "Jun", income: 4200, spending: 2600 },
        { name: "Jul", income: 5800, spending: 3400 },
        { name: "Aug", income: 4900, spending: 2100 },
        { name: "Sep", income: 7200, spending: 4100 },
        { name: "Oct", income: 6100, spending: 3200 },
        { name: "Nov", income: 8000, spending: 4600 },
      ];
  const shown = period === "This month" ? chart.slice(-1) : chart;
  const incomeTotal = shown.reduce((sum, p) => sum + Number(p.income), 0),
    spendingTotal = shown.reduce((sum, p) => sum + Number(p.spending), 0);

  return (
    <>
      <Title
        eyebrow="A LITTLE MORE POSSIBILITY, EVERY DAY"
        title={
          "Good morning, " +
          (profile?.display_name.split(" ")[0] || "there") +
          " ☀"
        }
        description="Here’s a clear view of your money. Make today a good one."
      >
        <span className="date-chip">
          <Clock size={15} />
          {new Date().toLocaleDateString("en-MY", {
            day: "numeric",
            month: "long",
            year: "numeric",
            timeZone: "Asia/Kuala_Lumpur",
          })}
        </span>
      </Title>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton" aria-label="Loading accounts" />
      ) : (
        <section className="overview-grid">
          <div className="balance-card">
            <div className="balance-card-top">
              <span>
                <span className="tiny-label">YOUR TOTAL BALANCE</span>
                <button
                  aria-label={visible ? "Hide balance" : "Show balance"}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <Eye size={16} /> : <EyeOff size={16} />}
                </button>
              </span>
              <span className="currency-pill">
                MYR <ChevronDown size={11} />
              </span>
            </div>
            <div className="total-balance">
              <span>RM</span> {visible ? money(total) : "••,•••.••"}
            </div>
            <div className="balance-caption">
              <span className="balance-tick">
                <Check size={12} />
              </span>
              Across {accounts.filter((a) => a.status !== "CLOSED").length} open
              accounts <span>·</span> Book balance
            </div>
            <div className="balance-card-bottom">
              <Link to="/transfer">
                Transfer money <ArrowUpRight size={17} />
              </Link>
              <Link to="/accounts">
                View accounts <ArrowRight size={15} />
              </Link>
            </div>
            <div className="balance-decoration" />
          </div>
          <div className="quick-actions panel">
            <div className="panel-title">
              <h2>Make your next move</h2>
              <span className="subtle">QUICK ACTIONS</span>
            </div>
            <div className="quick-action-grid">
              {[
                {
                  to: "/transfer",
                  Icon: ArrowUpRight,
                  name: "Send money",
                  desc: "A little goes a long way",
                },
                {
                  to: "/beneficiaries",
                  Icon: Users,
                  name: "Beneficiaries",
                  desc: "Your favourite people",
                },
                {
                  to: "/statements",
                  Icon: Download,
                  name: "Statements",
                  desc: "Every detail, on hand",
                },
                {
                  to: "/accounts",
                  Icon: Plus,
                  name: "My accounts",
                  desc: "Room for your plans",
                },
              ].map((a) => (
                <Link key={a.name} to={a.to}>
                  <span className="quick-icon">
                    <a.Icon size={20} />
                  </span>
                  <strong>{a.name}</strong>
                  <small>{a.desc}</small>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}
      <section className="accounts-section">
        <div className="section-heading">
          <h2>
            Your accounts{" "}
            <span className="count">
              {accounts.length.toString().padStart(2, "0")}
            </span>
          </h2>
          <Link to="/accounts">
            Manage accounts <ArrowRight size={15} />
          </Link>
        </div>
        <div className="account-cards">
          {accounts
            .filter((a) => a.status !== "CLOSED")
            .map((account, i) => (
              <AccountCard
                key={account.id}
                account={account}
                index={i}
                hide={!visible}
              />
            ))}
        </div>
      </section>
      <ErrorBox error={analytics.error} />
      <div className="analytics-grid">
        <section className="panel cashflow">
          <div className="panel-title">
            <div>
              <h2>Money in, money out</h2>
              <p>
                {live
                  ? "Recent activity in your accounts"
                  : "Your money’s journey, at a glance"}
              </p>
            </div>
            <label className="select-wrap">
              <select
                aria-label="Chart period"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
              >
                <option>This month</option>
                <option>Last 6 months</option>
              </select>
              <ChevronDown size={13} />
            </label>
          </div>
          <div className="chart-summary">
            <div>
              <span>
                <i className="dot income" /> Money in
              </span>
              <strong>RM {money(incomeTotal)}</strong>
            </div>
            <div>
              <span>
                <i className="dot spending" /> Money out
              </span>
              <strong>RM {money(spendingTotal)}</strong>
            </div>
            <span className="chart-period">
              {live
                ? "MALAYSIAN CALENDAR MONTHS"
                : period === "This month"
                  ? "NOV 2026 · ILLUSTRATIVE"
                  : "JUN–NOV 2026 · ILLUSTRATIVE"}
            </span>
          </div>
          <div
            className="chart"
            role="img"
            aria-label={
              live
                ? "Recent transaction credits and debits"
                : "Illustrative six-month income and spending chart"
            }
          >
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                data={chart}
                margin={{ top: 10, right: 0, left: -18, bottom: 0 }}
              >
                <defs>
                  <linearGradient id="fillIncome" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#376b55" stopOpacity={0.16} />
                    <stop offset="100%" stopColor="#376b55" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid
                  vertical={false}
                  stroke="#eef0ec"
                  strokeDasharray="3 4"
                />
                <XAxis
                  dataKey="name"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: "#8b928d", fontSize: 11 }}
                  dy={8}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: "#8b928d", fontSize: 10 }}
                  tickFormatter={(v) => (v === 0 ? "0" : v / 1000 + "k")}
                />
                <Tooltip formatter={(value) => "RM " + money(Number(value))} />
                <Area
                  type="monotone"
                  dataKey="income"
                  name="Money in"
                  stroke="#426f59"
                  strokeWidth={2.5}
                  fill="url(#fillIncome)"
                />
                <Area
                  type="monotone"
                  dataKey="spending"
                  name="Money out"
                  stroke="#d6aa58"
                  strokeWidth={2}
                  strokeDasharray="5 4"
                  fill="transparent"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </section>
        <section className="insight-card">
          <div className="insight-top">
            <span className="leaf">✧</span>
            <span>SMALL STEPS, BIG POSSIBILITIES</span>
          </div>
          <h2>
            A little saved.
            <br />A future shaped.
          </h2>
          <p>
            Give your goals a place to grow.
            <br />
            Move money into your savings
            <br />
            and build your next chapter.
          </p>
          <Link to="/transfer">
            Move to savings <ArrowUpRight size={17} />
          </Link>
          <div className="plant-art">
            <span className="plant-stem" />
            <i />
            <i />
            <i />
            <i />
            <i />
            <div className="plant-pot" />
            <span className="plant-orbit" />
          </div>
        </section>
      </div>
      <section className="panel recent">
        <div className="panel-title">
          <div>
            <h2>Recent activity</h2>
            <p>A closer look at your everyday.</p>
          </div>
          <Link to="/transactions">
            View all transactions <ArrowRight size={15} />
          </Link>
        </div>
        <ErrorBox error={history.error} />
        {history.isLoading ? (
          <p className="loading">Loading activity…</p>
        ) : (
          <TransactionTable items={history.data?.items || []} />
        )}
      </section>
      <div className="trust-strip">
        <ShieldCheck size={17} />
        <span>Built around your peace of mind.</span>
        <p>
          This is a banking simulation. All accounts and transactions are
          fictional.
        </p>
        <Link to="/security">
          Explore security <ArrowUpRight size={13} />
        </Link>
      </div>
    </>
  );
}
