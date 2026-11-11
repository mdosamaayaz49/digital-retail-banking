import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  LockKeyhole,
  Plus,
  ShieldCheck,
  X,
} from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import {
  api,
  login,
  money,
  previewBeneficiaries,
  type Beneficiary,
} from "../lib/banking";

import { ErrorBox, FieldError, Title } from "../components/banking";
import { useAccounts, useBank } from "../lib/context";
const transferSchema = z
  .object({
    sourceId: z.string().min(1, "Choose your account"),
    destinationId: z.string().min(1, "Choose a recipient"),
    amount: z
      .string()
      .regex(
        /^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/,
        "Use up to two decimal places",
      )
      .refine(
        (v) => Number(v) > 0 && Number(v) <= 10000,
        "Enter RM 0.01 to RM 10,000.00",
      ),
    description: z
      .string()
      .trim()
      .min(1, "Add a payment reference")
      .max(140)
      .regex(
        /^[\p{L}\p{N} .,'()/-]+$/u,
        "Use letters, numbers, spaces or basic punctuation",
      ),
  })
  .refine((v) => v.sourceId !== v.destinationId, {
    message: "Choose a different account",
    path: ["destinationId"],
  });
export type TransferForm = z.infer<typeof transferSchema>;
export function Transfer() {
  const { live, notify } = useBank();
  const query = useQueryClient();
  const { data: accounts = [] } = useAccounts();
  const beneficiaries = useQuery({
    queryKey: ["beneficiaries", live],
    queryFn: () =>
      live
        ? api<Beneficiary[]>("/beneficiaries")
        : Promise.resolve(previewBeneficiaries),
  });
  const [mode, setMode] = useState("recipient"),
    [review, setReview] = useState<TransferForm | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [success, setSuccess] = useState(""),
    [attempt, setAttempt] = useState({ body: "", key: "" });
  const form = useForm<TransferForm>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      sourceId: "",
      destinationId: "",
      amount: "",
      description: "",
    },
  });
  const source = form.watch("sourceId");
  const recipientOptions =
    mode === "own"
      ? accounts
          .filter((a) => a.id !== source)
          .map((a) => ({ id: a.id, label: a.name }))
      : beneficiaries.data?.map((b) => ({
          id: b.account_id,
          label: b.nickname + " · " + b.account_number.slice(-4),
        })) || [];
  async function confirm() {
    if (!review) return;
    if (!live) {
      setReview(null);
      notify(
        "This is a read-only preview. Sign in to make a simulated transfer.",
      );
      return;
    }
    setBusy(true);
    setError(null);
    const body = JSON.stringify(review);
    const key = attempt.body === body ? attempt.key : crypto.randomUUID();
    setAttempt({ body, key });
    try {
      const result = await api<{ reference: string }>("/transfers", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body,
      });
      setSuccess(result.reference);
      setReview(null);
      setAttempt({ body: "", key: "" });
      form.reset();
      await query.invalidateQueries();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Title
        eyebrow="SEND A LITTLE POSSIBILITY"
        title="Transfer money"
        description="Between your accounts or to someone who matters."
      />
      <div className="form-layout">
        <section className="panel form-panel">
          {success ? (
            <div className="success-state">
              <CheckCircle2 size={48} />
              <h2>Your transfer is complete.</h2>
              <p>The debit and credit were settled together.</p>
              <code>{success}</code>
              <button className="btn primary" onClick={() => setSuccess("")}>
                Make another transfer
              </button>
              <Link to="/transactions">
                View transaction history <ArrowRight size={15} />
              </Link>
            </div>
          ) : (
            <>
              <div className="form-tabs">
                <button
                  className={mode === "recipient" ? "active" : ""}
                  onClick={() => {
                    setMode("recipient");
                    form.setValue("destinationId", "");
                  }}
                >
                  To a beneficiary
                </button>
                <button
                  className={mode === "own" ? "active" : ""}
                  onClick={() => {
                    setMode("own");
                    form.setValue("destinationId", "");
                  }}
                >
                  Between my accounts
                </button>
              </div>
              <form
                onSubmit={form.handleSubmit((v) => {
                  setError(null);
                  setReview(v);
                })}
              >
                <label>
                  From account
                  <select {...form.register("sourceId")}>
                    <option value="">Select your account</option>
                    {accounts.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} · RM {money(a.availableBalance)}
                      </option>
                    ))}
                  </select>
                </label>
                <FieldError message={form.formState.errors.sourceId?.message} />
                <label>
                  {mode === "own" ? "To account" : "Beneficiary"}
                  <select {...form.register("destinationId")}>
                    <option value="">
                      Select a {mode === "own" ? "destination" : "beneficiary"}
                    </option>
                    {recipientOptions.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.label}
                      </option>
                    ))}
                  </select>
                </label>
                <FieldError
                  message={form.formState.errors.destinationId?.message}
                />
                {mode === "recipient" && (
                  <Link className="inline-link" to="/beneficiaries">
                    <Plus size={14} />
                    Add a new beneficiary
                  </Link>
                )}
                <label>
                  Amount
                  <span className="amount-input">
                    <b>MYR</b>
                    <input
                      inputMode="decimal"
                      placeholder="0.00"
                      {...form.register("amount")}
                    />
                  </span>
                </label>
                <FieldError message={form.formState.errors.amount?.message} />
                <small className="field-hint">
                  Per transfer: RM 10,000.00 · Daily limit: RM 25,000.00
                </small>
                <label>
                  Payment reference
                  <input
                    placeholder="What’s this transfer for?"
                    maxLength={140}
                    {...form.register("description")}
                  />
                </label>
                <FieldError
                  message={form.formState.errors.description?.message}
                />
                <ErrorBox error={error} />
                <button className="btn primary full" type="submit">
                  Review transfer <ArrowRight size={17} />
                </button>
              </form>
            </>
          )}
        </section>
        <aside className="transfer-aside">
          <div className="aside-icon">
            <ShieldCheck size={26} />
          </div>
          <h2>A little reassurance.</h2>
          <p>
            Your simulated transfers are protected with account ownership checks
            and atomic double-entry accounting.
          </p>
          <div>
            <CheckCircle2 size={16} />
            <span>No transfer fees</span>
          </div>
          <div>
            <CheckCircle2 size={16} />
            <span>Instant internal settlement</span>
          </div>
          <div>
            <CheckCircle2 size={16} />
            <span>Duplicate request protection</span>
          </div>
          <hr />
          <small>
            All funds are fictional. DuitNow-style transfers and external
            payment networks are planned simulations, not live integrations.
          </small>
          {!live && (
            <button
              className="btn secondary"
              onClick={() => void login().catch((e) => notify(String(e)))}
            >
              Sign in to try a transfer <ArrowUpRight size={16} />
            </button>
          )}
        </aside>
      </div>
      {review && (
        <div className="modal-backdrop">
          <section
            className="modal"
            onKeyDown={(e) => {
              if (e.key === "Escape" && !busy) setReview(null);
              if (e.key === "Tab") {
                const buttons = Array.from(
                  e.currentTarget.querySelectorAll<HTMLButtonElement>(
                    "button:not(:disabled)",
                  ),
                );
                const first = buttons[0],
                  last = buttons[buttons.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                  e.preventDefault();
                  last?.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                  e.preventDefault();
                  first?.focus();
                }
              }
            }}
            role="dialog"
            aria-modal="true"
            aria-labelledby="review-title"
          >
            <button
              className="close icon-btn"
              aria-label="Close review"
              disabled={busy}
              onClick={() => setReview(null)}
            >
              <X size={20} />
            </button>
            <div className="aside-icon">
              <ArrowUpRight size={26} />
            </div>
            <h2 id="review-title">One last look.</h2>
            <p>Confirm the details of your simulated transfer.</p>
            <div className="review-amount">RM {money(review.amount)}</div>
            <dl>
              <dt>From</dt>
              <dd>{accounts.find((a) => a.id === review.sourceId)?.name}</dd>
              <dt>To</dt>
              <dd>
                {
                  recipientOptions.find((b) => b.id === review.destinationId)
                    ?.label
                }
              </dd>
              <dt>Reference</dt>
              <dd>{review.description}</dd>
              <dt>Transfer fee</dt>
              <dd>RM 0.00</dd>
            </dl>
            <ErrorBox error={error} />
            <button
              autoFocus
              className="btn primary full"
              disabled={busy}
              onClick={() => void confirm()}
            >
              {busy
                ? "Settling transfer…"
                : live
                  ? "Confirm transfer"
                  : "Continue in preview"}
              <LockKeyhole size={16} />
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
