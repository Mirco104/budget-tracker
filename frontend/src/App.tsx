import { useState } from 'react'
import { currentMonth } from './format'
import { DashboardPage } from './pages/DashboardPage'
import { SettingsPage } from './pages/SettingsPage'
import { TransactionsPage } from './pages/TransactionsPage'

type Page = 'dashboard' | 'transactions' | 'settings'

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
          aria-current={page === 'settings' ? 'page' : undefined}
          onClick={() => setPage('settings')}
        >
          Einstellungen
        </button>
        <label>
          Monat{' '}
          <input type="month" value={month} required onChange={(event) => setMonth(event.target.value)} />
        </label>
      </nav>

      {page === 'dashboard' && month && <DashboardPage month={month} />}
      {page === 'transactions' && month && <TransactionsPage month={month} />}
      {page === 'settings' && <SettingsPage />}
    </main>
  )
}

export default App
