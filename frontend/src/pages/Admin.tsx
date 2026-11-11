import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  Check,
  ChevronLeft,
  ChevronRight,
  FileText,
  LockKeyhole,
  RotateCcw,
  ShieldCheck,
  Users,
  Wallet,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { Badge, Empty, ErrorBox } from "../components/banking";
import { api, date, identity, money, type Page } from "../lib/banking";
import { useBank } from "../lib/context";
import {
  Holds,
  NotificationRecovery,
  Reconciliation,
  Reports,
} from "./Operations";
type Row = Record<string, string | number>;
export default function Admin() {
  const { live } = useBank();
  if (!live || !identity.hasRealmRole("ADMIN"))
    return (
      <section className="panel login-panel">
        <ShieldCheck size={40} />
        <h1>Administrator access</h1>
        <p>
          Sign in as the fictional reviewer identity to access simulated KYC,
          account controls and financial audit history.
        </p>
      </section>
    );
  return (
    <>
      <div className="eyebrow">ADMINISTRATION · SIMULATION</div>
      <h1>Banking operations</h1>
      <p className="page-description">
        A clear view of the people, accounts and activity behind the scenes.
      </p>
      <nav className="admin-tabs">
        <NavLink to="/admin" end>
          Overview
        </NavLink>
        <NavLink to="/admin/customers">Customers & KYC</NavLink>
        <NavLink to="/admin/accounts">Accounts</NavLink>
        <NavLink to="/admin/transactions">Transaction monitoring</NavLink>
        <NavLink to="/admin/audit-logs">Audit logs</NavLink>
        <NavLink to="/admin/holds">Holds</NavLink>
        <NavLink to="/admin/reconciliation">Reconciliation</NavLink>
        <NavLink to="/admin/reports">Reports</NavLink>
        <NavLink to="/admin/notifications">Recovery</NavLink>
      </nav>
      <Routes>
        <Route path="holds" element={<Holds />} />
        <Route path="reconciliation" element={<Reconciliation />} />
        <Route path="reports" element={<Reports />} />
        <Route path="notifications" element={<NotificationRecovery />} />
        <Route index element={<Summary />} />
        <Route path="customers" element={<Data kind="customers" />} />
        <Route path="accounts" element={<Data kind="accounts" />} />
        <Route path="transactions" element={<Data kind="transactions" />} />
        <Route path="audit-logs" element={<Data kind="audit-logs" />} />
      </Routes>
    </>
  );
}
function Summary() {
  const q = useQuery({
    queryKey: ["admin-summary"],
    queryFn: () => api<Record<string, number>>("/admin/summary"),
  });
  return (
    <>
      <ErrorBox error={q.error} />
      <div className="summary-grid">
        {[
          { key: "customers", name: "Fictional customers", Icon: Users },
          { key: "accounts", name: "Customer accounts", Icon: Wallet },
          {
            key: "transfers",
            name: "Simulated transfers",
            Icon: ArrowLeftRight,
          },
          { key: "pendingKyc", name: "Pending KYC reviews", Icon: ShieldCheck },
          {
            key: "pendingEvents",
            name: "Pending outbox events",
            Icon: FileText,
          },
        ].map((c) => (
          <section key={c.key} className="panel summary-card">
            <c.Icon size={23} />
            <strong>{q.data?.[c.key] ?? "—"}</strong>
            <span>{c.name}</span>
          </section>
        ))}
      </div>
      <div className="notice space-top">
        KYC is simulated. Transfers above RM 5,000 are highlighted for
        educational review; this is not an AML screening engine.
      </div>
    </>
  );
}
function Data({ kind }: { kind: string }) {
  const [page, setPage] = useState(0),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState("");
  const query = useQueryClient();
  const q = useQuery({
    queryKey: ["admin", kind, page],
    queryFn: () =>
      api<Page<Row>>("/admin/" + kind + "?page=" + page + "&size=20"),
  });
  const columns =
    kind === "customers"
      ? ["display_name", "email", "kyc_status"]
      : kind === "accounts"
        ? ["name", "display_name", "account_number", "balance", "status"]
        : kind === "transactions"
          ? ["reference", "amount", "description", "status", "created_at"]
          : ["actor", "action", "resource", "created_at"];
  async function mutate(path: string, method: string, body?: object) {
    setBusy(path);
    setError(null);
    try {
      await api(path, {
        method,
        body: body ? JSON.stringify(body) : undefined,
      });
      await query.invalidateQueries();
    } catch (e) {
      setError(e);
    } finally {
      setBusy("");
    }
  }
  return (
    <section className="panel">
      <ErrorBox error={error || q.error} />
      {q.isLoading ? (
        <p className="loading">Loading records…</p>
      ) : !q.data?.items.length ? (
        <Empty>No records found.</Empty>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c}>{c.replaceAll("_", " ")}</th>
                ))}
                {kind !== "audit-logs" && <th>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {q.data?.items.map((row, i) => (
                <tr key={String(row.id ?? i)}>
                  {columns.map((c) => (
                    <td key={c}>
                      {["status", "kyc_status"].includes(c) ? (
                        <Badge status={String(row[c])} />
                      ) : c === "balance" || c === "amount" ? (
                        <span>
                          {"RM " + money(row[c])}
                          {c === "amount" && Number(row[c]) > 5000 && (
                            <small className="review-flag">Review flag</small>
                          )}
                        </span>
                      ) : c === "created_at" ? (
                        date(String(row[c]))
                      ) : (
                        <span
                          className={
                            c === "reference" || c === "resource" ? "mono" : ""
                          }
                        >
                          {row[c]}
                        </span>
                      )}
                    </td>
                  ))}
                  {kind !== "audit-logs" && (
                    <td>
                      <div className="row-actions">
                        {kind === "customers" ? (
                          <>
                            <button
                              className="btn mini secondary"
                              disabled={!!busy || row.kyc_status === "VERIFIED"}
                              onClick={() =>
                                void mutate(
                                  "/admin/customers/" + row.id + "/kyc",
                                  "PATCH",
                                  { status: "VERIFIED" },
                                )
                              }
                            >
                              <Check size={13} />
                              Verify
                            </button>
                            <button
                              className="btn mini text"
                              disabled={!!busy || row.kyc_status === "REJECTED"}
                              onClick={() =>
                                void mutate(
                                  "/admin/customers/" + row.id + "/kyc",
                                  "PATCH",
                                  { status: "REJECTED" },
                                )
                              }
                            >
                              Reject
                            </button>
                          </>
                        ) : kind === "accounts" ? (
                          <button
                            className="btn mini secondary"
                            disabled={!!busy}
                            onClick={() =>
                              void mutate(
                                "/admin/accounts/" + row.id + "/status",
                                "PATCH",
                                {
                                  status:
                                    row.status === "ACTIVE"
                                      ? "FROZEN"
                                      : "ACTIVE",
                                },
                              )
                            }
                          >
                            <LockKeyhole size={13} />
                            {row.status === "ACTIVE" ? "Freeze" : "Unfreeze"}
                          </button>
                        ) : (
                          <button
                            className="btn mini secondary"
                            disabled={
                              !!busy ||
                              row.status === "REVERSED" ||
                              !!row.reversal_of ||
                              !row.customer_id
                            }
                            onClick={() => {
                              if (
                                window.confirm(
                                  "Reverse this transfer using compensating ledger entries?",
                                )
                              )
                                void mutate(
                                  "/admin/transfers/" + row.id + "/reverse",
                                  "POST",
                                );
                            }}
                          >
                            <RotateCcw size={13} />
                            Reverse
                          </button>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="pagination">
        <span>
          {q.data?.total || 0} records · Page {page + 1}
        </span>
        <button
          disabled={!page}
          onClick={() => setPage(page - 1)}
          aria-label="Previous page"
        >
          <ChevronLeft size={17} />
        </button>
        <button
          disabled={(page + 1) * 20 >= (q.data?.total || 0)}
          onClick={() => setPage(page + 1)}
          aria-label="Next page"
        >
          <ChevronRight size={17} />
        </button>
      </div>
    </section>
  );
}
