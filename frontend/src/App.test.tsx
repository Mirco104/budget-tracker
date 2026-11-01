import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

// Fictional signed-in user; all other calls stay pending
const me = { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'USER' }

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

async function renderApp() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => (url === '/api/auth/me' ? json(me) : new Promise(() => {}))),
  )
  render(
    <QueryClientProvider client={new QueryClient()}>
      <App />
    </QueryClientProvider>,
  )
  await screen.findByRole('button', { name: 'Übersicht' })
}

describe('App', () => {
  it('renders the app title', async () => {
    await renderApp()

    expect(screen.getByRole('heading', { name: 'Budget-Tracker' })).toBeInTheDocument()
  })

  it('moves the selected month with the previous and next buttons', async () => {
    await renderApp()
    const monthInput = screen.getByLabelText('Monat')
    fireEvent.change(monthInput, { target: { value: '2026-12' } })

    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))
    expect(monthInput).toHaveValue('2027-01')

    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    expect(monthInput).toHaveValue('2026-11')
  })

  it('jumps back to the current month', async () => {
    await renderApp()
    const monthInput = screen.getByLabelText('Monat')
    const today = new Date()
    const current = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
    const currentButton = screen.getByRole('button', { name: 'Aktueller Monat' })
    expect(currentButton).toBeDisabled()

    fireEvent.change(monthInput, { target: { value: '2020-05' } })
    expect(currentButton).toBeEnabled()
    fireEvent.click(currentButton)

    expect(monthInput).toHaveValue(current)
  })

  it('has the tabs Übersicht, Transaktionen, Planung, Auswertung and Einstellungen', async () => {
    await renderApp()

    expect(screen.getByRole('button', { name: 'Übersicht' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Transaktionen' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Wiederkehrend' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Einstellungen' })).toBeInTheDocument()
  })

  it('keeps the month selection on the planning page', async () => {
    await renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Planung' }))

    expect(screen.getByLabelText('Monat')).toBeInTheDocument()
  })
  // The analysis page is loaded on first use, because of the chart library
  it('loads the analysis page when its tab is opened', async () => {
    await renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Auswertung' }))

    expect(screen.getByText('Lade Auswertung …')).toBeInTheDocument()
    // Transforming Recharts for the first import takes a while in the test environment
    await waitFor(
      () => expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/reserves\?month=.*&months=12$/), expect.anything()),
      { timeout: 5000 },
    )
  })

  // US10: settings do not depend on a month
  it('hides the month selection on the settings page', async () => {
    await renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Einstellungen' }))

    expect(screen.getByRole('heading', { name: 'Einstellungen' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Monat')).not.toBeInTheDocument()
  })

  // US14
  it('shows the login page without a session and signs out again', async () => {
    let signedIn = false
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/auth/me') {
        return signedIn ? json(me) : json({ status: 401, title: 'Nicht angemeldet' }, 401)
      }
      if (url === '/api/auth/login') {
        signedIn = true
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url === '/api/auth/logout' && init?.method === 'POST') {
        signedIn = false
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return new Promise(() => {})
    })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <QueryClientProvider client={new QueryClient()}>
        <App />
      </QueryClientProvider>,
    )

    await screen.findByRole('heading', { name: 'Anmelden' })
    expect(screen.queryByRole('button', { name: 'Übersicht' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: 'person-a@example.test' } })
    fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: 'test-only-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }))

    expect(await screen.findByText(/Angemeldet als Person A/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Abmelden' }))

    await screen.findByRole('heading', { name: 'Anmelden' })
  })
})
