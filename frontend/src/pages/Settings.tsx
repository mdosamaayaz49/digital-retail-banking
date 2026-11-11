import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  ExternalLink,
  Globe,
  Landmark,
  LockKeyhole,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { api, identity, login, logout } from "../lib/banking";

import { Badge, ErrorBox, Title } from "../components/banking";
import { useBank, useProfile } from "../lib/context";
export function ProfilePage() {
  const { live, notify } = useBank();
  const q = useProfile();
  const query = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const form = useForm({
    defaultValues: { displayName: "", email: "", phone: "" },
  });
  useEffect(() => {
    if (q.data)
      form.reset({
        displayName: q.data.display_name,
        email: q.data.email,
        phone: q.data.phone || "",
      });
  }, [q.data, form]);
  async function save(v: {
    displayName: string;
    email: string;
    phone: string;
  }) {
    if (!live) {
      notify("Sign in to edit your fictional profile.");
      return;
    }
    try {
      await api("/customers/me", {
        method: q.error ? "POST" : "PATCH",
        body: JSON.stringify(v),
      });
      await query.invalidateQueries({ queryKey: ["profile"] });
      notify("Profile saved.");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <>
      <Title
        eyebrow="MAKE YOURSELF AT HOME"
        title="Profile & settings"
        description="Your details, kept up to date."
      />
      <div className="form-layout">
        <section className="panel form-panel">
          <h2>Personal information</h2>
          <ErrorBox error={error} />
          {q.error && (
            <div className="notice">
              New registration? Add your fictional details to provision a
              customer profile.
            </div>
          )}
          <form onSubmit={form.handleSubmit(save)}>
            <label>
              Display name
              <input
                required
                maxLength={100}
                {...form.register("displayName")}
              />
            </label>
            <label>
              Email address
              <input
                required
                type="email"
                maxLength={200}
                {...form.register("email")}
              />
            </label>
            <label>
              Phone number
              <input
                pattern="[+0-9 -]{5,30}"
                placeholder="+60 10 000 0001"
                {...form.register("phone")}
              />
            </label>
            <button
              className="btn primary"
              disabled={form.formState.isSubmitting}
            >
              Save changes <Check size={17} />
            </button>
          </form>
        </section>
        <aside className="transfer-aside">
          <ShieldCheck size={30} />
          <h2>Your verification</h2>
          <Badge status={q.data?.kyc_status || "PENDING"} />
          <p>
            This is a simulated KYC status. No identity documents or real
            personal information should be supplied.
          </p>
          <Link className="inline-link" to="/security">
            Manage security <ArrowRight size={15} />
          </Link>
        </aside>
      </div>
    </>
  );
}
export function Security() {
  const { live, notify } = useBank();
  return (
    <>
      <Title
        eyebrow="BANK WITH PEACE OF MIND"
        title="Security centre"
        description="Simple habits. Stronger protection."
      />
      <div className="security-grid">
        {[
          {
            Icon: LockKeyhole,
            title: "Password & sign-in",
            text: "Passwords, account recovery and active sessions are managed securely by Keycloak.",
            action: "Manage account",
            run: () =>
              live
                ? void identity.accountManagement()
                : void login().catch((e) => notify(String(e))),
          },
          {
            Icon: ShieldCheck,
            title: "Two-step verification",
            text: "Enable an authenticator in Keycloak’s account console. MFA is optional in this local demonstration.",
            action: "Set up authenticator",
            run: () =>
              live
                ? void identity.accountManagement()
                : void login().catch((e) => notify(String(e))),
          },
          {
            Icon: Globe,
            title: "Your banking session",
            text: live
              ? "Your short-lived access token is kept in memory. Sign out to end your identity-provider session."
              : "You are browsing a read-only preview. Sign in for secured API access.",
            action: live ? "Sign out of session" : "Sign in securely",
            run: () =>
              live
                ? void logout()
                : void login().catch((e) => notify(String(e))),
          },
        ].map((c) => (
          <section key={c.title} className="panel security-card">
            <c.Icon size={28} />
            <h2>{c.title}</h2>
            <p>{c.text}</p>
            <button className="btn secondary" onClick={c.run}>
              {c.action}
              <ExternalLink size={15} />
            </button>
          </section>
        ))}
      </div>
      <div className="notice space-top">
        Never use real banking credentials, real identity documents or real
        funds in this portfolio application.
      </div>
    </>
  );
}
export function Login({ register = false }: { register?: boolean }) {
  const { notify } = useBank();
  return (
    <div className="login-panel panel">
      <span className="brand-mark">
        <Landmark size={30} />
      </span>
      <span className="eyebrow">A LITTLE MORE POSSIBILITY</span>
      <h1>
        {register
          ? "Your next chapter starts here."
          : "Welcome to your everyday."}
      </h1>
      <p>
        {register
          ? "Create a fictional customer identity with our secure identity provider."
          : "Sign in through Keycloak to explore your simulated banking accounts."}
      </p>
      <button
        className="btn primary full"
        onClick={() => login(register).catch((e) => notify(String(e)))}
      >
        {register ? "Create fictional identity" : "Continue to secure sign-in"}
        <ArrowUpRight size={17} />
      </button>
      <Link to={register ? "/login" : "/register"}>
        {register
          ? "Already registered? Sign in"
          : "New here? Create an identity"}
      </Link>
      <small>
        Portfolio simulation · Never enter real banking information.
      </small>
    </div>
  );
}
export function Help() {
  return (
    <>
      <Title
        title="A little clarity."
        description="Everything you need to know about this demonstration."
      />
      <section className="panel help-content">
        <h2>Welcome to Nusantara</h2>
        <p>
          This is an independent Java full stack portfolio project with
          fictional customers and simulated MYR funds. It has no affiliation
          with a real bank, PayNet, DuitNow or Bank Negara Malaysia.
        </p>
        <h2>Try the banking journey</h2>
        <p>
          Start the Docker Compose stack, then sign in as amira or daniel using
          the generated DEMO_PASSWORD in your local .env file. The reviewer
          identity has administrative access. Add a beneficiary, transfer
          simulated funds, and download a PDF statement.
        </p>
        <h2>About the preview</h2>
        <p>
          The read-only preview contains illustrative activity and chart data.
          Sign in to view database-backed balances and transactions. Financial
          operations always execute on the Java backend.
        </p>
        <h2>Need to reset?</h2>
        <p>
          See README.md for startup, testing and local demo reset instructions.
          A reset deletes local simulation history and must be an intentional
          developer operation.
        </p>
      </section>
    </>
  );
}
