import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileText } from "lucide-react";
import { useState } from "react";
import { api, date, downloadStatement } from "../lib/banking";

import { Empty, ErrorBox, Title } from "../components/banking";
import { useAccounts, useBank } from "../lib/context";
export function Statements() {
  const { live, notify } = useBank();
  const { data: accounts = [] } = useAccounts();
  const query = useQueryClient();
  const [account, setAccount] = useState(""),
    [month, setMonth] = useState(new Date().toISOString().slice(0, 7)),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState(false);
  type Statement = {
    id: string;
    date_from: string;
    date_to: string;
    account_name: string;
  };
  const q = useQuery({
    queryKey: ["statements", live],
    queryFn: () =>
      live ? api<Statement[]>("/statements") : Promise.resolve([]),
  });
  async function generate(e: React.FormEvent) {
    e.preventDefault();
    if (!live) {
      notify("Sign in to generate a PDF from the real simulation ledger.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const last = new Date(
        Number(month.slice(0, 4)),
        Number(month.slice(5, 7)),
        0,
      ).getDate();
      const end = month + "-" + last;
      const today = new Date().toLocaleDateString("en-CA", {
        timeZone: "Asia/Kuala_Lumpur",
      });
      const result = await api<{ id: string }>("/statements/generate", {
        method: "POST",
        body: JSON.stringify({
          accountId: account,
          from: month + "-01",
          to: end > today ? today : end,
        }),
      });
      await query.invalidateQueries({ queryKey: ["statements"] });
      await downloadStatement(result.id);
      notify("Your PDF statement is ready.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Title
        eyebrow="YOUR MONEY, ON RECORD"
        title="Bank statements"
        description="The full picture, down to the last sen."
      />
      <div className="form-layout">
        <section className="panel form-panel">
          <h2>Generate a statement</h2>
          <p>Opening balance, every posting, and your closing balance.</p>
          <form onSubmit={generate}>
            <label>
              Account
              <select
                required
                value={account}
                onChange={(e) => setAccount(e.target.value)}
              >
                <option value="">Select an account</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} · {a.accountNumber.slice(-4)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Statement month
              <input
                required
                type="month"
                value={month}
                max={new Date().toISOString().slice(0, 7)}
                onChange={(e) => setMonth(e.target.value)}
              />
            </label>
            <ErrorBox error={error} />
            <button className="btn primary full" disabled={busy}>
              <Download size={17} />
              {busy ? "Generating PDF…" : "Generate & download PDF"}
            </button>
          </form>
        </section>
        <aside className="transfer-aside">
          <FileText size={32} />
          <h2>Every entry tells a story.</h2>
          <p>
            Your statement is generated from the ledger. Reversals remain
            visible as separate compensating entries.
          </p>
          <small>
            PDFs contain fictional customer information. These are demonstration
            documents only.
          </small>
        </aside>
      </div>
      <section className="panel space-top">
        <div className="panel-title">
          <h2>Your statements</h2>
        </div>
        <ErrorBox error={q.error} />
        {!q.data?.length ? (
          <Empty>
            No statements generated yet. Your first one is just above.
          </Empty>
        ) : (
          q.data.map((s) => (
            <div className="statement-row" key={s.id}>
              <FileText size={22} />
              <span>
                <strong>{s.account_name}</strong>
                <small>
                  {date(s.date_from)} – {date(s.date_to)}
                </small>
              </span>
              <button
                className="btn secondary"
                onClick={() => downloadStatement(s.id).catch(setError)}
              >
                <Download size={16} />
                PDF
              </button>
            </div>
          ))
        )}
      </section>
    </>
  );
}
