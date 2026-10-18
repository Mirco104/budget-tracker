import { useState } from 'react'
import { currentMonth } from './format'
import { DashboardPage } from './pages/DashboardPage'

type Page = 'dashboard'

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
        <label>
          Monat{' '}
          <input type="month" value={month} required onChange={(event) => setMonth(event.target.value)} />
        </label>
      </nav>

      {page === 'dashboard' && month && <DashboardPage month={month} />}
    </main>
  )
}

export default App
