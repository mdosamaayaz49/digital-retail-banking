import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Search,
} from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  api,
  money,
  previewTransactions,
  type Page,
  type Transaction,
} from "../lib/banking";

import {
  Badge,
  Empty,
  ErrorBox,
  Title,
  TransactionTable,
} from "../components/banking";
import { useBank } from "../lib/context";
export function History({ accountId }: { accountId?: string }) {
  const { live } = useBank();
  const [search, setSearch] = useState(""),
    [from, setFrom] = useState(""),
    [to, setTo] = useState(""),
    [page, setPage] = useState(0);
  const params = new URLSearchParams({
    page: String(page),
    size: "10",
    search,
  });
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const q = useQuery({
    queryKey: ["history", live, accountId, page, search, from, to],
    queryFn: () => {
      if (live)
        return api<Page<Transaction>>(
          (accountId
            ? "/accounts/" + accountId + "/transactions"
            : "/transactions") +
            "?" +
            params,
        );
      const rows = previewTransactions.filter(
        (t) =>
          (!accountId || t.account_id === accountId) &&
          t.description.toLowerCase().includes(search.toLowerCase()) &&
          (!from || t.created_at >= from) &&
          (!to || t.created_at.substring(0, 10) <= to),
      );
      return Promise.resolve({
        items: rows.slice(page * 10, page * 10 + 10),
        page,
        size: 10,
        total: rows.length,
      });
    },
  });
  return (
    <>
      {!accountId && (
        <Title
          eyebrow="EVERY DETAIL, IN ONE PLACE"
          title="Transaction history"
          description="Follow the little moments that move your money."
        />
      )}
      <div className="filter-row">
        <label className="search-box">
          <Search size={17} />
          <input
            aria-label="Search transactions"
            placeholder="Search transactions or references"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="date-filter">
          From
          <input
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="date-filter">
          To
          <input
            type="date"
            min={from}
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      <section className="panel">
        <ErrorBox error={q.error} />
        {q.isLoading ? (
          <p className="loading">Loading transactions…</p>
        ) : (
          <TransactionTable items={q.data?.items || []} />
        )}
        <div className="pagination">
          <span>
            {q.data?.total || 0} transactions · Page {page + 1}
          </span>
          <button
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
            aria-label="Previous page"
          >
            <ChevronLeft size={17} />
          </button>
          <button
            disabled={(page + 1) * 10 >= (q.data?.total || 0)}
            onClick={() => setPage(page + 1)}
            aria-label="Next page"
          >
            <ChevronRight size={17} />
          </button>
        </div>
      </section>
    </>
  );
}
export function TransactionDetails() {
  const { id } = useParams();
  const { live } = useBank();
  const { data, error, isLoading } = useQuery({
    queryKey: ["transaction", id, live],
    queryFn: () =>
      live
        ? api<Record<string, string | number>>("/transfers/" + id)
        : Promise.resolve(
            previewTransactions.find((t) => t.id === id) as unknown as Record<
              string,
              string | number
            >,
          ),
  });
  return (
    <>
      <Link className="back-link" to="/transactions">
        <ArrowLeft size={15} />
        All transactions
      </Link>
      <Title
        title="Transaction details"
        description="A clear record of your simulated transfer."
      />
      <ErrorBox error={error} />
      {isLoading ? (
        <p>Loading details…</p>
      ) : data ? (
        <section className="panel detail-list">
          <span className="success-symbol">
            <CheckCircle2 size={28} />
          </span>
          <h2>RM {money(Math.abs(Number(data.amount)))}</h2>
          <Badge status={String(data.status)} />
          {[
            "reference",
            "description",
            "currency",
            "created_at",
            "source_id",
            "destination_id",
          ]
            .filter((k) => data[k])
            .map((k) => (
              <div key={k}>
                <span>{k.replaceAll("_", " ")}</span>
                <strong>{String(data[k])}</strong>
              </div>
            ))}
        </section>
      ) : (
        <Empty>Transaction not found.</Empty>
      )}
    </>
  );
}
