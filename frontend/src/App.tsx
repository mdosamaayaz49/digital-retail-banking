import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  ArrowUpRight,
  Bell,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  FileText,
  Landmark,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Menu,
  ReceiptText,
  Settings,
  ShieldCheck,
  Users,
  Wallet,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import {
  Link,
  NavLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  api,
  date,
  identity,
  initAuth,
  login,
  logout,
  type Profile,
} from "./lib/banking";

import { ErrorBox } from "./components/banking";
import { Context } from "./lib/context";
import { AccountDetails, Accounts } from "./pages/Accounts";
import Admin from "./pages/Admin";
import { Beneficiaries } from "./pages/Beneficiaries";
import { Dashboard } from "./pages/Dashboard";
import { Help, Login, ProfilePage, Security } from "./pages/Settings";
import { Statements } from "./pages/Statements";
import { History, TransactionDetails } from "./pages/Transactions";
import { Transfer } from "./pages/Transfer";
const nav = [
  { to: "/", name: "Overview", icon: LayoutDashboard },
  { to: "/accounts", name: "My accounts", icon: Wallet },
  { to: "/transfer", name: "Transfer money", icon: ArrowLeftRight },
  { to: "/beneficiaries", name: "Beneficiaries", icon: Users },
  { to: "/transactions", name: "Transactions", icon: ReceiptText },
  { to: "/statements", name: "Statements", icon: FileText },
];
export default function App() {
  const [live, setLive] = useState(false),
    [authError, setAuthError] = useState(""),
    [toast, setToast] = useState(""),
    [mobile, setMobile] = useState(false),
    [notifications, setNotifications] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const query = useQueryClient();
  useEffect(() => {
    initAuth()
      .then((auth) => {
        setLive(auth);
        if (auth && identity.hasRealmRole("ADMIN") && location.pathname === "/")
          navigate("/admin", { replace: true });
      })
      .catch(() =>
        setAuthError(
          "Identity service is unavailable. You can explore the read-only preview.",
        ),
      );
    identity.onTokenExpired = () => {
      identity.updateToken(40).catch(() => {
        setLive(false);
        setToast("Session expired. Please sign in again.");
      });
    };
  }, []);
  useEffect(() => {
    setMobile(false);
  }, [location.pathname]);
  useEffect(() => {
    if (toast) {
      const t = setTimeout(() => setToast(""), 6000);
      return () => clearTimeout(t);
    }
  }, [toast]);
  const profile = useQuery({
    queryKey: ["profile", live],
    queryFn: () =>
      live
        ? api<Profile>("/customers/me")
        : Promise.resolve({
            display_name: "Amira Rahman",
            email: "amira@example.test",
            phone: "",
            kyc_status: "VERIFIED",
          }),
  });
  const notices = useQuery({
    queryKey: ["notifications", live],
    queryFn: () =>
      api<{ event_id: string; message: string; created_at: string }[]>(
        "/notifications",
      ),
    enabled: live && notifications,
  });
  const admin = identity.hasRealmRole("ADMIN");
  return (
    <Context.Provider value={{ live, notify: setToast }}>
      <div className="app-shell">
        <a href="#content" className="skip">
          Skip to content
        </a>
        <aside className={mobile ? "sidebar open" : "sidebar"}>
          <Link className="brand" to="/" aria-label="Nusantara home">
            <span className="brand-mark">
              <Landmark size={23} />
            </span>
            <span>
              nusantara<span className="brand-dot">.</span>
              <small>DIGITAL BANKING</small>
            </span>
          </Link>
          <div className="workspace-label">PERSONAL BANKING</div>
          <nav aria-label="Main navigation">
            {nav.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.to === "/"}>
                <n.icon size={19} />
                <span>{n.name}</span>
                {n.to === "/transfer" && <span className="nav-dot" />}
              </NavLink>
            ))}
          </nav>
          <div className="sidebar-divider" />
          <div className="workspace-label">PREFERENCES</div>
          <nav aria-label="Preferences">
            <NavLink to="/profile">
              <Settings size={19} />
              Profile & settings
            </NavLink>
            <NavLink to="/security">
              <ShieldCheck size={19} />
              Security centre
            </NavLink>
            {admin && (
              <NavLink to="/admin">
                <Landmark size={19} />
                Administration
              </NavLink>
            )}
          </nav>
          <div className="sidebar-bottom">
            <div className="help-card">
              <span className="help-icon">
                <CircleHelp size={19} />
              </span>
              <strong>A little help?</strong>
              <p>
                Good banking starts with
                <br />
                peace of mind.
              </p>
              <Link to="/help">
                Visit help centre <ArrowUpRight size={14} />
              </Link>
            </div>
            <div className="secure-line">
              <LockKeyhole size={13} />
              Your connection is secure
            </div>
            <button
              className="logout"
              onClick={() =>
                live
                  ? void logout()
                  : void login().catch((e) => setToast(String(e)))
              }
            >
              {live ? <LogOut size={17} /> : <LockKeyhole size={17} />}{" "}
              {live ? "Sign out" : "Sign in to banking"}
            </button>
          </div>
        </aside>
        <div className="main-shell">
          <header className="topbar">
            <div className="topbar-left">
              <button
                className="icon-btn mobile-menu"
                aria-label="Toggle navigation"
                onClick={() => setMobile(!mobile)}
              >
                <Menu size={21} />
              </button>
              <span className="breadcrumb">
                Personal banking <ChevronRight size={13} />
                <b>
                  {nav.find((n) => n.to === location.pathname)?.name ||
                    "Your banking"}
                </b>
              </span>
            </div>
            <div className="topbar-right">
              <span className={"environment " + (live ? "" : "preview")}>
                <span />
                {live ? "Secure session" : "Read-only preview"}
              </span>
              <button
                className="notification icon-btn"
                aria-label="Notifications"
                aria-expanded={notifications}
                onClick={() => setNotifications(!notifications)}
              >
                <Bell size={19} />
                {live && <i />}
              </button>
              <span className="header-separator" />
              <Link className="user" to="/profile">
                <span className="avatar">{admin ? "AR" : "AR"}</span>
                <span>
                  <strong>
                    {admin
                      ? "Aisha · Admin"
                      : profile.data?.display_name || "Your profile"}
                  </strong>
                  <small>
                    {live ? "Personal account" : "Fictional customer"}
                  </small>
                </span>
                <ChevronDown size={14} />
              </Link>
            </div>
            {notifications && (
              <div className="notification-panel">
                <h3>Your notifications</h3>
                {!live ? (
                  <p>
                    Sign in to see notifications from simulated transactions.
                  </p>
                ) : notices.isLoading ? (
                  <p>Loading notifications…</p>
                ) : notices.error ? (
                  <ErrorBox error={notices.error} />
                ) : notices.data?.length ? (
                  notices.data.map((n) => (
                    <p key={n.event_id}>
                      {n.message}
                      <small>{date(n.created_at)}</small>
                    </p>
                  ))
                ) : (
                  <p>You’re all caught up.</p>
                )}
              </div>
            )}
          </header>
          <main id="content" className="main-content">
            {authError && <div className="notice">{authError}</div>}
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/accounts" element={<Accounts />} />
              <Route path="/accounts/:id" element={<AccountDetails />} />
              <Route path="/transfer" element={<Transfer />} />
              <Route path="/beneficiaries" element={<Beneficiaries />} />
              <Route path="/transactions" element={<History />} />
              <Route
                path="/transactions/:id"
                element={<TransactionDetails />}
              />
              <Route path="/statements" element={<Statements />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/security" element={<Security />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Login register />} />
              <Route path="/admin/*" element={<Admin />} />
              <Route path="/help" element={<Help />} />
              <Route
                path="*"
                element={
                  <>
                    <h1>Page not found</h1>
                    <Link to="/">Return to overview</Link>
                  </>
                }
              />
            </Routes>
          </main>
          <footer>
            <span>© 2026 Nusantara. A personal banking portfolio project.</span>
            <span>
              <span className="footer-dot" /> Fictional people. Simulated funds.{" "}
              <Link to="/help">
                About this demo <ArrowUpRight size={12} />
              </Link>
            </span>
          </footer>
        </div>
        {toast && (
          <div role="status" className="toast">
            <CheckCircle2 size={20} />
            {toast}
            <button aria-label="Dismiss message" onClick={() => setToast("")}>
              <X size={16} />
            </button>
          </div>
        )}
      </div>
    </Context.Provider>
  );
}
