import { lazy, Suspense, useState } from 'react'
import { useLogout, useMe } from './api/auth'
import type { Me } from './api/types'
import { currentMonth, shiftMonth } from './format'
import { AcceptInvitationPage } from './pages/AcceptInvitationPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PlanningPage } from './pages/PlanningPage'
import { SettingsPage } from './pages/SettingsPage'
import { TransactionsPage } from './pages/TransactionsPage'

// Loaded when the tab is first opened: the page brings Recharts, which would otherwise double the initial bundle
const ReservesPage = lazy(() => import('./pages/ReservesPage').then((module) => ({ default: module.ReservesPage })))

type Page = 'dashboard' | 'transactions' | 'planning' | 'reserves' | 'settings'

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
      <main>
        <h1>Budget-Tracker</h1>
        <AcceptInvitationPage token={token} onAccepted={handleAccepted} />
      </main>
    )
  }

  return (
    <main>
      <h1>Budget-Tracker</h1>
      {me.isPending && <p>Lade …</p>}
      {me.error && <p className="error">Der Server ist nicht erreichbar: {me.error.message}</p>}
      {me.data === null && <LoginPage />}
      {me.data && <SignedIn me={me.data} />}
    </main>
  )
}

function SignedIn({ me }: { me: Me }) {
  const logout = useLogout()
  const [page, setPage] = useState<Page>('dashboard')
  const [month, setMonth] = useState(currentMonth)

  return (
    <>
      <p className="account">
        Angemeldet als {me.displayName}{' '}
        <button type="button" disabled={logout.isPending} onClick={() => logout.mutate()}>
          Abmelden
        </button>
      </p>
      <nav>
        <button
          type="button"
          aria-current={page === 'dashboard' ? 'page' : undefined}
          onClick={() => setPage('dashboard')}
        >
          Übersicht
        </button>
        <button
          type="button"
          aria-current={page === 'transactions' ? 'page' : undefined}
          onClick={() => setPage('transactions')}
        >
          Transaktionen
        </button>
        <button
          type="button"
          aria-current={page === 'planning' ? 'page' : undefined}
          onClick={() => setPage('planning')}
        >
          Planung
        </button>
        <button
          type="button"
          aria-current={page === 'reserves' ? 'page' : undefined}
          onClick={() => setPage('reserves')}
        >
          Auswertung
        </button>
        <button
          type="button"
          aria-current={page === 'settings' ? 'page' : undefined}
          onClick={() => setPage('settings')}
        >
          Einstellungen
        </button>
        {page !== 'settings' && (
          <span className="month-nav">
            <button
              type="button"
              aria-label="Vorheriger Monat"
              disabled={!month}
              onClick={() => setMonth(shiftMonth(month, -1))}
            >
              ‹
            </button>
            <label>
              Monat{' '}
              <input type="month" value={month} required onChange={(event) => setMonth(event.target.value)} />
            </label>
            <button
              type="button"
              aria-label="Nächster Monat"
              disabled={!month}
              onClick={() => setMonth(shiftMonth(month, 1))}
            >
              ›
            </button>
            <button type="button" disabled={month === currentMonth()} onClick={() => setMonth(currentMonth())}>
              Aktueller Monat
            </button>
          </span>
        )}
      </nav>

      {page === 'dashboard' && month && <DashboardPage month={month} />}
      {page === 'transactions' && month && <TransactionsPage month={month} />}
      {page === 'planning' && month && <PlanningPage month={month} />}
      {page === 'reserves' && month && (
        <Suspense fallback={<p>Lade Auswertung …</p>}>
          <ReservesPage month={month} />
        </Suspense>
      )}
      {page === 'settings' && <SettingsPage me={me} />}
    </>
  )
}

export default App
