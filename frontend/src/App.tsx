import { useState } from 'react'
import { currentMonth, shiftMonth } from './format'
import { DashboardPage } from './pages/DashboardPage'
import { SettingsPage } from './pages/SettingsPage'
import { TransactionsPage } from './pages/TransactionsPage'
import { YearlyTransactionsPage } from './pages/YearlyTransactionsPage'

type Page = 'dashboard' | 'transactions' | 'yearly' | 'settings'

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
          Dashboard
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
          aria-current={page === 'yearly' ? 'page' : undefined}
          onClick={() => setPage('yearly')}
        >
          Jährliche Transaktionen
        </button>
        <button
          type="button"
          aria-current={page === 'settings' ? 'page' : undefined}
          onClick={() => setPage('settings')}
        >
          Einstellungen
        </button>
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
      </nav>

      {page === 'dashboard' && month && <DashboardPage month={month} />}
      {page === 'transactions' && month && <TransactionsPage month={month} />}
      {page === 'yearly' && month && <YearlyTransactionsPage month={month} />}
      {page === 'settings' && month && <SettingsPage month={month} />}
    </main>
  )
}

export default App
