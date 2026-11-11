import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Plus } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import { api, previewBeneficiaries, type Beneficiary } from "../lib/banking";

import {
  Badge,
  Empty,
  ErrorBox,
  FieldError,
  Title,
} from "../components/banking";
import { useBank } from "../lib/context";
export function Beneficiaries() {
  const { live, notify } = useBank();
  const query = useQueryClient();
  const [adding, setAdding] = useState(false),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState(false);
  const q = useQuery({
    queryKey: ["beneficiaries", live],
    queryFn: () =>
      live
        ? api<Beneficiary[]>("/beneficiaries")
        : Promise.resolve(previewBeneficiaries),
  });
  const schema = z.object({
    nickname: z.string().trim().min(1).max(80),
    accountNumber: z.string().min(8).max(20),
  });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
  });
  async function save(v: z.infer<typeof schema>) {
    if (!live) {
      notify("Sign in to save a fictional beneficiary.");
      return;
    }
    setBusy(true);
    try {
      await api("/beneficiaries", { method: "POST", body: JSON.stringify(v) });
      await query.invalidateQueries({ queryKey: ["beneficiaries"] });
      setAdding(false);
      form.reset();
      notify("Beneficiary added.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function remove(id: string) {
    if (!live) {
      notify("Sign in to manage beneficiaries.");
      return;
    }
    if (!window.confirm("Remove this beneficiary?")) return;
    try {
      await api("/beneficiaries/" + id, { method: "DELETE" });
      await query.invalidateQueries({ queryKey: ["beneficiaries"] });
      notify("Beneficiary removed.");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <>
      <Title
        eyebrow="YOUR FAVOURITE PEOPLE"
        title="Beneficiaries"
        description="Less typing. More time for the people you know."
      >
        <button className="btn primary" onClick={() => setAdding(!adding)}>
          <Plus size={17} />
          Add beneficiary
        </button>
      </Title>
      <ErrorBox error={q.error || error} />
      {adding && (
        <form
          className="panel form-panel compact space-bottom"
          onSubmit={form.handleSubmit(save)}
        >
          <h2>Add a fictional beneficiary</h2>
          <label>
            Nickname
            <input {...form.register("nickname")} />
          </label>
          <FieldError message={form.formState.errors.nickname?.message} />
          <label>
            Account number
            <input
              placeholder="1100 5532 0816"
              {...form.register("accountNumber")}
            />
          </label>
          <FieldError message={form.formState.errors.accountNumber?.message} />
          <small className="field-hint">
            Demo recipient Daniel: 1100 5532 0816
          </small>
          <button className="btn primary" disabled={busy}>
            Save beneficiary
          </button>
        </form>
      )}
      <div className="beneficiary-grid">
        {q.data?.map((b) => (
          <section className="panel beneficiary-card" key={b.id}>
            <span className="large-avatar">
              {b.nickname
                .split(" ")
                .map((p) => p[0])
                .slice(0, 2)
                .join("")}
            </span>
            <h2>{b.nickname}</h2>
            <p>Nusantara · {b.account_number}</p>
            <Badge status="ACTIVE" />
            <div>
              <Link className="btn secondary" to="/transfer">
                Send money <ArrowUpRight size={15} />
              </Link>
              <button className="btn text" onClick={() => void remove(b.id)}>
                Remove
              </button>
            </div>
          </section>
        ))}
      </div>
      {q.isLoading ? (
        <p>Loading beneficiaries…</p>
      ) : (
        !q.data?.length && (
          <Empty>Add a beneficiary to make your first transfer.</Empty>
        )
      )}
    </>
  );
}
