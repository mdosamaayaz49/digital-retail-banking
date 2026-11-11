import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronRight,
  ReceiptText,
  Wallet,
} from "lucide-react";
import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import { date, money, type Account, type Transaction } from "../lib/banking";

export function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div className="error" role="alert">
      {error instanceof Error ? error.message : String(error)}
    </div>
  ) : null;
}
export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="empty">
      <ReceiptText size={30} />
      <p>{children}</p>
    </div>
  );
}
export function Badge({ status }: { status: string }) {
  return (
    <span
      className={
        "badge " +
        (["ACTIVE", "VERIFIED", "SETTLED"].includes(status)
          ? "green"
          : status === "REJECTED" || status === "FROZEN"
            ? "red"
            : "amber")
      }
    >
      <span />
      {status === "SETTLED"
        ? "Completed"
        : status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}
export function Title({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="heading-actions">{children}</div>
    </div>
  );
}
export function AccountCard({
  account,
  index,
  hide = false,
}: {
  account: Account;
  index: number;
  hide?: boolean;
}) {
  return (
    <Link to={"/accounts/" + account.id} className="account-card">
      <div className="account-card-head">
        <span className={"account-icon " + (index % 2 ? "gold" : "")}>
          <Wallet size={19} />
        </span>
        <div>
          <h3>{account.name}</h3>
          <p>
            {account.type === "SAVINGS" ? "SAVINGS ACCOUNT" : "CURRENT ACCOUNT"}{" "}
            <span>•• {account.accountNumber.slice(-4)}</span>
          </p>
        </div>
        <ChevronRight size={17} />
      </div>
      <div className="account-card-foot">
        <div>
          <small>Available balance</small>
          <strong>
            <span>RM</span>{" "}
            {hide ? "••,•••.••" : money(account.availableBalance)}
          </strong>
        </div>
        <Badge status={account.status} />
      </div>
    </Link>
  );
}
export function TransactionTable({ items }: { items: Transaction[] }) {
  if (!items.length) return <Empty>No transactions match this view.</Empty>;
  return (
    <div className="table-scroll">
      <table className="transaction-table">
        <thead>
          <tr>
            <th>Transaction</th>
            <th>Account</th>
            <th>Date</th>
            <th>Status</th>
            <th className="align-right">Amount</th>
            <th>
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((t, i) => (
            <tr key={t.id + "-" + t.account_id + "-" + i}>
              <td>
                <Link className="transaction-name" to={"/transactions/" + t.id}>
                  <span
                    className={
                      "transaction-icon " + (t.amount > 0 ? "credit" : "")
                    }
                  >
                    {t.amount > 0 ? (
                      <ArrowDownLeft size={19} />
                    ) : (
                      <ArrowUpRight size={19} />
                    )}
                  </span>
                  <span>
                    <strong>{t.description}</strong>
                    <small>
                      {t.amount > 0 ? "Incoming transfer" : "Outgoing transfer"}
                    </small>
                  </span>
                </Link>
              </td>
              <td>{t.account_name}</td>
              <td>{date(t.created_at)}</td>
              <td>
                <Badge status={t.status} />
              </td>
              <td
                className={
                  "align-right amount " + (t.amount > 0 ? "positive" : "")
                }
              >
                {t.amount > 0 ? "+" : "−"} RM {money(Math.abs(t.amount))}
              </td>
              <td>
                <Link
                  to={"/transactions/" + t.id}
                  aria-label={"Details for " + t.description}
                >
                  <ChevronRight size={15} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function FieldError({ message }: { message?: string }) {
  return message ? (
    <span className="field-error" role="alert">
      {message}
    </span>
  ) : null;
}
