import { lazy, Suspense, useState, type ReactNode } from 'react'
import { useLogout, useMe } from './api/auth'
import type { Me } from './api/types'
import { AccountMenu } from './components/ui/AccountMenu'
import { Icon, type IconName } from './components/ui/Icon'
import { currentMonth } from './format'
import { AcceptInvitationPage } from './pages/AcceptInvitationPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PlanningPage } from './pages/PlanningPage'
import { SettingsPage } from './pages/SettingsPage'
import { TransactionsRoute } from './pages/TransactionsPage'
import { ROUTES, usePage, type Page } from './router'

// Loaded when the tab is first opened: the page brings Recharts, which would otherwise double the initial bundle
const EvaluationPage = lazy(() =>
  import('./pages/EvaluationPage').then((module) => ({ default: module.EvaluationPage })),
)

// The main tabs (US29); the settings live in the account menu
const TABS: { page: Page; label: string; icon: IconName }[] = [
  { page: 'overview', label: 'Übersicht', icon: 'overview' },
  { page: 'transactions', label: 'Transaktionen', icon: 'transactions' },
  { page: 'planning', label: 'Planung', icon: 'planning' },
  { page: 'evaluation', label: 'Auswertung', icon: 'evaluation' },
]

// US15: an invitation link opens the app with ?einladung=<token>
function invitationToken(): string | null {
  return new URLSearchParams(window.location.search).get('einladung')
}

/** Every page needs a signed-in user; without one the login page shows (US14). */
function App() {
  const me = useMe()
  const [token, setToken] = useState(invitationToken)

  function handleAccepted() {
    window.history.replaceState(null, '', window.location.pathname)
    setToken(null)
  }

  if (token) {
    return (
      <SignedOut>
        <AcceptInvitationPage token={token} onAccepted={handleAccepted} />
      </SignedOut>
    )
  }
  if (me.data) {
    return <SignedIn me={me.data} />
  }
  return (
    <SignedOut>
      {me.isPending && <p>Lade …</p>}
      {me.error && <p className="error">Der Server ist nicht erreichbar: {me.error.message}</p>}
      {me.data === null && <LoginPage />}
    </SignedOut>
  )
}

function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark" aria-hidden="true">
        B
      </span>
      Budget-Tracker
    </div>
  )
}

// Static pages in public/, reachable with and without sign-in (section 15)
function LegalLinks() {
  return (
    <footer className="legal">
      <a href="/impressum.html">Impressum</a>
      <a href="/datenschutz.html">Datenschutz</a>
    </footer>
  )
}

/** Login and invitation: one centered card. */
function SignedOut({ children }: { children: ReactNode }) {
  return (
    <main className="auth-page">
      <Brand />
      <div className="card">{children}</div>
      <LegalLinks />
    </main>
  )
}

function SignedIn({ me }: { me: Me }) {
  const logout = useLogout()
  const page = usePage()
  // The month of the overview; it stays when switching pages (decision 28: the other pages have their own time)
  const [month, setMonth] = useState(currentMonth)

  return (
    <>
      <header className="app-header">
        <div className="app-header-inner">
          <Brand />
          <nav className="tabs" aria-label="Hauptnavigation">
            {TABS.map((tab) => (
              <a
                key={tab.page}
                className="tab"
                href={ROUTES[tab.page]}
                aria-current={page === tab.page ? 'page' : undefined}
              >
                <Icon name={tab.icon} />
                {tab.label}
              </a>
            ))}
          </nav>
          <AccountMenu
            me={me}
            settingsHref={ROUTES.settings}
            signingOut={logout.isPending}
            onSignOut={() => logout.mutate()}
          />
        </div>
      </header>

      <main className="content">
        {page === 'overview' && <DashboardPage month={month} onMonthChange={setMonth} />}
        {page === 'transactions' && <TransactionsRoute />}
        {page === 'planning' && <PlanningPage />}
        {page === 'evaluation' && (
          <Suspense fallback={<p>Lade Auswertung …</p>}>
            <EvaluationPage />
          </Suspense>
        )}
        {page === 'settings' && <SettingsPage me={me} />}
        <LegalLinks />
      </main>
    </>
  )
}

export default App
