import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { currentMonth, shiftMonth } from './format'

// Fictional signed-in user; all other calls stay pending
const me = { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'USER' }

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderWithClient() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <App />
    </QueryClientProvider>,
  )
}

async function renderApp() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => (url === '/api/auth/me' ? json(me) : new Promise(() => {}))),
  )
  renderWithClient()
  await screen.findByRole('navigation', { name: 'Hauptnavigation' })
}

/** Like a click on a link or the browser's back button: the hash changes and the app follows. */
function goTo(hash: string) {
  act(() => {
    window.location.hash = hash
    window.dispatchEvent(new HashChangeEvent('hashchange'))
  })
}

afterEach(() => {
  window.history.replaceState(null, '', '/')
})

describe('App', () => {
  it('shows the brand and opens the overview by default', async () => {
    await renderApp()

    expect(screen.getByText('Budget-Tracker')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Übersicht' })).toBeInTheDocument()
  })

  // US29: four tabs as links; the settings live in the account menu
  it('has the tabs Übersicht, Transaktionen, Planung and Auswertung as links', async () => {
    await renderApp()

    const links = ['Übersicht', 'Transaktionen', 'Planung', 'Auswertung'].map((name) =>
      screen.getByRole('link', { name }),
    )
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '#/uebersicht',
      '#/transaktionen',
      '#/planung',
      '#/auswertung',
    ])
    expect(links[0]).toHaveAttribute('aria-current', 'page')
    expect(screen.queryByRole('link', { name: 'Einstellungen' })).not.toBeInTheDocument()
  })

  it('follows the address, also for the back button', async () => {
    await renderApp()

    goTo('#/planung')
    expect(screen.getByRole('heading', { level: 1, name: 'Planung' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Planung' })).toHaveAttribute('aria-current', 'page')

    goTo('#/uebersicht')
    expect(screen.getByRole('heading', { level: 1, name: 'Übersicht' })).toBeInTheDocument()
  })

  it('opens the page from the address on start', async () => {
    window.history.replaceState(null, '', '/#/transaktionen')

    await renderApp()

    expect(screen.getByRole('heading', { level: 1, name: 'Transaktionen' })).toBeInTheDocument()
  })

  it('shows the overview for an unknown address', async () => {
    window.history.replaceState(null, '', '/#/gibt-es-nicht')

    await renderApp()

    expect(screen.getByRole('heading', { level: 1, name: 'Übersicht' })).toBeInTheDocument()
  })

  it('moves the month in the page header and jumps back to the current month', async () => {
    await renderApp()
    const current = currentMonth()
    const field = screen.getByLabelText('Monat wählen')
    expect(field).toHaveValue(current)
    // Hidden but still there, so the navigation does not move when it appears
    expect(screen.queryByRole('button', { name: 'Zum aktuellen Monat' })).not.toBeInTheDocument()
    expect(screen.getByText('Zum aktuellen Monat')).toHaveStyle({ visibility: 'hidden' })

    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))
    expect(field).toHaveValue(shiftMonth(current, 1))

    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    expect(field).toHaveValue(shiftMonth(current, -1))

    fireEvent.click(screen.getByRole('button', { name: 'Zum aktuellen Monat' }))
    expect(field).toHaveValue(current)
  })

  it('takes a month that is picked or typed in the field', async () => {
    await renderApp()
    const field = screen.getByLabelText('Monat wählen')

    fireEvent.change(field, { target: { value: '2024-03' } })
    expect(field).toHaveValue('2024-03')
    expect(screen.getByRole('button', { name: 'Zum aktuellen Monat' })).toBeInTheDocument()

    // An incomplete entry is ignored, the month stays
    fireEvent.change(field, { target: { value: '' } })
    expect(field).toHaveValue('2024-03')
  })

  it('keeps the chosen month when switching pages', async () => {
    await renderApp()
    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))

    goTo('#/auswertung')

    expect(await screen.findByLabelText('Monat wählen', {}, { timeout: 15000 })).toHaveValue(
      shiftMonth(currentMonth(), 1),
    )
  }, 20000)

  // US26: the transactions have their own period, starting at this month
  it('shows the transactions of this month as a period', async () => {
    await renderApp()

    goTo('#/transaktionen')

    expect(await screen.findByRole('button', { name: 'Dieser Monat' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByLabelText('Monat wählen')).not.toBeInTheDocument()
  })

  // Decision 28: the planning page has its own month "Gilt ab", starting at the current month
  it('starts the planning at the current month', async () => {
    await renderApp()
    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))

    goTo('#/planung')

    expect(await screen.findByLabelText('Gilt ab', { selector: 'input' })).toHaveValue(currentMonth())
  })

  // The analysis page is loaded on first use, because of the chart library
  it('loads the analysis page when its tab is opened', async () => {
    await renderApp()

    goTo('#/auswertung')

    expect(screen.getByText('Lade Auswertung …')).toBeInTheDocument()
    // Transforming Recharts for the first import takes a while in the test environment
    await waitFor(
      () => expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/reserves\?month=.*&months=12$/), expect.anything()),
      { timeout: 5000 },
    )
  })

  it('opens the account menu with name, settings and sign-out', async () => {
    await renderApp()
    const avatar = screen.getByRole('button', { name: 'Konto und Einstellungen' })
    expect(avatar).toHaveTextContent('PA')
    expect(avatar).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(avatar)

    expect(avatar).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('person-a@example.test')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Einstellungen' })).toHaveAttribute('href', '#/einstellungen')
    expect(screen.getByRole('button', { name: 'Abmelden' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('link', { name: 'Einstellungen' })).not.toBeInTheDocument()
  })

  // US10: settings do not depend on a month
  it('shows the settings without a month navigation', async () => {
    await renderApp()

    goTo('#/einstellungen')

    expect(screen.getByRole('heading', { level: 1, name: 'Einstellungen' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Monat' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Übersicht' })).not.toHaveAttribute('aria-current')
  })

  // Section 15: imprint and privacy notice, also without sign-in
  it('links to the imprint and the privacy notice on the login page', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json({ status: 401, title: 'Nicht angemeldet' }, 401)),
    )
    renderWithClient()

    await screen.findByRole('heading', { name: 'Anmelden' })
    expect(screen.getByRole('link', { name: 'Impressum' })).toHaveAttribute('href', '/impressum.html')
    expect(screen.getByRole('link', { name: 'Datenschutz' })).toHaveAttribute('href', '/datenschutz.html')
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
    renderWithClient()

    await screen.findByRole('heading', { name: 'Anmelden' })
    expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: 'person-a@example.test' } })
    fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: 'test-only-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Konto und Einstellungen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abmelden' }))

    await screen.findByRole('heading', { name: 'Anmelden' })
  })
})
