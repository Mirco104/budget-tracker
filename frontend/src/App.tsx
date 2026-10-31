import { lazy, Suspense, useState } from 'react'
import { currentMonth, shiftMonth } from './format'
import { DashboardPage } from './pages/DashboardPage'
import { PlanningPage } from './pages/PlanningPage'
import { SettingsPage } from './pages/SettingsPage'
import { TransactionsPage } from './pages/TransactionsPage'

// Loaded when the tab is first opened: the page brings Recharts, which would otherwise double the initial bundle
const ReservesPage = lazy(() => import('./pages/ReservesPage').then((module) => ({ default: module.ReservesPage })))

type Page = 'dashboard' | 'transactions' | 'planning' | 'reserves' | 'settings'

function App() {
  const [page, setPage] = useState<Page>('dashboard')
  const [month, setMonth] = useState(currentMonth)

  return (
    <main>
      <h1>Budget-Tracker</h1>
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
      {page === 'settings' && <SettingsPage />}
    </main>
  )
}

export default App
