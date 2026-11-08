import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BankAccount, Category, RecurringTransaction, ReserveData, Transaction } from '../api/types'
import { TransactionsPage, TransactionsRoute } from './TransactionsPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 6, name: 'Freizeit', icon: 'music', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt', icon: 'wallet', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: true },
]

const transactions: Transaction[] = [
  { kind: 'RECURRING', id: null, recurringId: 3, occurrenceDate: '2026-10-05', amount: -15, description: 'Streaming', date: '2026-10-05', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
  { kind: 'SINGLE', id: 8, recurringId: null, occurrenceDate: null, amount: 3000, description: 'Lohn', date: '2026-10-20', categoryId: 20, categoryName: 'Gehalt', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
  { kind: 'SINGLE', id: 7, recurringId: null, occurrenceDate: null, amount: -12.1, description: 'Kino', date: '2026-10-20', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
  { kind: 'SINGLE', id: 9, recurringId: null, occurrenceDate: null, amount: -40, description: 'Konzert', date: '2026-10-28', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
]

const series: RecurringTransaction[] = [
  { id: 3, seriesId: 'a1', amount: -15, description: 'Streaming', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', interval: 'MONTHLY', startDate: '2026-01-05', endDate: null, monthlyEquivalent: 15 },
]

// Only the fields the reserve summary uses.
const reserves: Pick<ReserveData, 'monthlyReserve' | 'reserveBalance' | 'reserves'> = {
  monthlyReserve: 20,
  reserveBalance: 100,
  reserves: [
    { recurringId: 4, description: 'Haftpflicht', categoryName: 'Versicherungen', amount: 240, interval: 'YEARLY', nextDate: '2027-10-15', monthly: 20, saved: 100, ratesLeft: 7 },
  ],
}

function renderPage(entries: Transaction[] = transactions, bankAccounts: BankAccount[] = []) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === 'DELETE') {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    const body = init?.method
      ? {}
      : url.startsWith('/api/transactions')
        ? entries
        : url.startsWith('/api/categories')
          ? categories
          : url.startsWith('/api/reserves')
            ? reserves
            : url.startsWith('/api/bank-accounts')
              ? bankAccounts
              : series
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TransactionsPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

function day(name: string) {
  return screen.getByRole('region', { name })
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 21))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // R27, US31: a transfer is marked, counts in no sum and has its own kind in the filter
  it('marks transfers, leaves them out of the sums and filters them by kind', async () => {
    const transfer: Transaction = {
      kind: 'SINGLE', id: 99, recurringId: null, occurrenceDate: null, amount: -500, description: 'Aufs Tagesgeld',
      date: '2026-10-05', categoryId: null, categoryName: null, accountId: 1, accountName: 'Girokonto', transfer: true,
      replaces: null,
    }
    renderPage([...transactions, transfer])

    const row = await screen.findByRole('button', { name: /Aufs Tagesgeld/ })
    expect(row).toHaveTextContent('Umbuchung')
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'TRANSFER' } })
    expect(screen.getByText(/1 Eintrag · Einnahmen 0,00\s€ · Ausgaben 0,00\s€/)).toBeInTheDocument()
    expect(within(day('Montag, 5. Oktober 2026')).getByRole('heading')).not.toHaveTextContent('500')
    // A day with only the transfer has the sum 0 without a sign
    expect(within(day('Montag, 5. Oktober 2026')).getByRole('heading')).toHaveTextContent(/0,00\s€$/)
    expect(within(day('Montag, 5. Oktober 2026')).getByRole('heading')).not.toHaveTextContent('−0,00')
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'SINGLE' } })
    expect(screen.queryByRole('button', { name: /Aufs Tagesgeld/ })).not.toBeInTheDocument()
  })

  // US33: the import has its own address below Transaktionen, and a period in the address bar starts the list
  it('links to the import and starts with the period from the address bar', async () => {
    window.location.hash = '#/transaktionen?von=2026-10-05&bis=2026-10-20'
    const fetchMock = vi.fn((url: string) => Promise.resolve(new Response(JSON.stringify(
      url.startsWith('/api/categories') ? categories : url.startsWith('/api/reserves') ? reserves : [],
    ), { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TransactionsRoute />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('link', { name: 'Importieren' })).toHaveAttribute('href', '#/transaktionen/import')
    expect(screen.getByLabelText('Von')).toHaveValue('2026-10-05')
    expect(screen.getByLabelText('Bis')).toHaveValue('2026-10-20')
    await waitFor(() =>
      expect(fetchMock.mock.calls.map(([url]) => String(url))).toContain('/api/transactions?from=2026-10-05&to=2026-10-20'),
    )
    window.location.hash = ''
  })

  // R28: a booking that replaces an occurrence is marked recurring and found with the kind Wiederkehrend
  it('marks a booking that replaces an occurrence as recurring', async () => {
    const replacing: Transaction = {
      kind: 'SINGLE', id: 98, recurringId: null, occurrenceDate: null, amount: -905, description: 'Miete Bank',
      date: '2026-10-02', categoryId: 1, categoryName: 'Wohnen', accountId: 1, accountName: 'Girokonto', transfer: false,
      replaces: { recurringId: 4, date: '2026-10-01' },
    }
    renderPage([...transactions, replacing])

    const row = await screen.findByRole('button', { name: /Miete Bank/ })
    expect(row).toHaveTextContent('wiederkehrend')
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'SINGLE' } })
    expect(screen.queryByRole('button', { name: /Miete Bank/ })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'RECURRING' } })
    expect(screen.getByRole('button', { name: /Miete Bank/ })).toBeInTheDocument()
  })

  // US30: with more than one account each entry names its account
  it('names the account of each entry when there is more than one account', async () => {
    renderPage(transactions, [
      { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true },
      { id: 2, name: 'Kreditkarte', iban: null, defaultAccount: false, archived: false, used: false },
    ])

    const kino = await screen.findByRole('button', { name: /Kino/ })
    await waitFor(() => expect(kino).toHaveTextContent('· Girokonto'))
  })

  // After the second test (07.11.2026): a filter for the account with more than one account
  it('filters the entries by account', async () => {
    const onCard: Transaction = { ...transactions[0], id: 97, description: 'Tankstelle', accountId: 2, accountName: 'Kreditkarte' }
    renderPage([...transactions, onCard], [
      { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true },
      { id: 2, name: 'Kreditkarte', iban: null, defaultAccount: false, archived: false, used: true },
    ])
    await screen.findByRole('button', { name: /Tankstelle/ })

    fireEvent.change(await screen.findByLabelText('Konto filtern'), { target: { value: '2' } })

    expect(screen.getByRole('button', { name: /Tankstelle/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Kino/ })).not.toBeInTheDocument()
    expect(screen.getByText(/1 Eintrag ·/)).toBeInTheDocument()
  })

  it('offers no account filter with only one account', async () => {
    renderPage(transactions, [{ id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true }])
    await screen.findByRole('button', { name: /Kino/ })

    expect(screen.queryByLabelText('Konto filtern')).not.toBeInTheDocument()
  })

  it('names no account with only one account', async () => {
    renderPage(transactions, [{ id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true }])

    const kino = await screen.findByRole('button', { name: /Kino/ })
    expect(kino).not.toHaveTextContent('Girokonto')
  })

  // Decision 30: grouped by day, newest first, with the sum of the day
  it('groups the entries by day, newest day first, with the sum of each day', async () => {
    renderPage()

    await screen.findByText('Kino')
    const days = screen.getAllByRole('region').map((region) => region.getAttribute('aria-label'))
    expect(days).toEqual(['Mittwoch, 28. Oktober 2026', 'Dienstag, 20. Oktober 2026', 'Montag, 5. Oktober 2026'])
    const twentieth = day('Dienstag, 20. Oktober 2026')
    expect(within(twentieth).getByRole('heading')).toHaveTextContent('+2.987,90 €')
    expect(within(twentieth).getAllByRole('button').map((b) => b.textContent)).toEqual([
      expect.stringContaining('Lohn'),
      expect.stringContaining('Kino'),
    ])
    expect(within(day('Montag, 5. Oktober 2026')).getByRole('heading')).toHaveTextContent('−15,00 €')
  })

  it('shows each entry with icon, category, signed amount and its markers', async () => {
    renderPage()

    const kino = await screen.findByRole('button', { name: /Kino/ })
    expect(within(kino).getByText('Freizeit')).toBeInTheDocument()
    // Expenses in the normal text color; orange is reserved for warnings (decision 32)
    expect(within(kino).getByText('−12,10 €')).not.toHaveClass('negative')
    expect(within(screen.getByRole('button', { name: /Lohn/ })).getByText('+3.000,00 €')).toHaveClass('positive')
    expect(kino.querySelector('.category-icon')).toHaveAttribute('data-icon', 'music')
    expect(within(screen.getByRole('button', { name: /Streaming/ })).getByText('wiederkehrend')).toBeInTheDocument()
    expect(within(kino).queryByText('wiederkehrend')).not.toBeInTheDocument()
    // US26: entries after today are planned
    expect(within(screen.getByRole('button', { name: /Konzert/ })).getByText('geplant')).toBeInTheDocument()
    expect(within(kino).queryByText('geplant')).not.toBeInTheDocument()
  })

  it('has no buttons per row and no form above the list', async () => {
    renderPage()

    await screen.findByText('Kino')
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Löschen' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Beschreibung')).not.toBeInTheDocument()
    expect(screen.queryByText(/Wiederkehrende Transaktionen/)).not.toBeInTheDocument()
  })

  it('shows the monthly reserve and what should be saved by now', async () => {
    renderPage()

    expect(
      await screen.findByText('Rücklagen: monatlich 20,00 € zurücklegen, jetzt angespart 100,00 €.'),
    ).toBeInTheDocument()
  })

  it('says so when the period has no entries', async () => {
    renderPage([])

    expect(await screen.findByText('Keine Einträge in diesem Zeitraum.')).toBeInTheDocument()
  })

  // US27: "+ Buchung" and the round button on phones open the dialog
  it('opens the booking dialog with "+ Buchung" and with the round button', async () => {
    renderPage()
    await screen.findByText('Kino')

    fireEvent.click(screen.getByRole('button', { name: 'Buchung' }))
    expect(screen.getByRole('dialog', { name: 'Neue Buchung' })).toBeInTheDocument()
    // Today in the month of the page
    expect(screen.getByLabelText('Datum')).toHaveValue('2026-10-21')
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Neue Buchung' }))
    expect(screen.getByRole('dialog', { name: 'Neue Buchung' })).toBeInTheDocument()
  })

  it('opens a row for editing with a click', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: /Kino/ }))

    const dialog = screen.getByRole('dialog', { name: 'Buchung bearbeiten' })
    expect(within(dialog).getByLabelText('Betrag in Euro')).toHaveValue('12,10')
    expect(within(dialog).getByLabelText('Beschreibung')).toHaveValue('Kino')
  })

  it('opens an occurrence with the question for the scope', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: /Streaming/ }))

    const dialog = screen.getByRole('dialog', { name: 'Wiederkehrende Buchung bearbeiten' })
    expect(within(dialog).getByRole('radio', { name: 'Nur dieser Eintrag' })).toBeChecked()
    expect(within(dialog).getByRole('radio', { name: 'Dieser und alle folgenden' })).toBeInTheDocument()
  })

  it('closes the dialog and confirms briefly after saving', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Kino/ }))

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Änderung gespeichert.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions/7', expect.objectContaining({ method: 'PUT' }))
  })

  it('closes the dialog and confirms briefly after deleting', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Kino/ }))

    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Buchung gelöscht.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  // Only Date is faked, so the toast's timer runs in real time
  it('hides the confirmation after a few seconds', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Kino/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await screen.findByRole('status')

    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument(), { timeout: 4000 })
  })

  // US26: default this month, as a period
  it('loads this month as a period and shows period, count and sums', async () => {
    const fetchMock = renderPage()

    await screen.findByText('Kino')
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions?from=2026-10-01&to=2026-10-31', expect.anything())
    expect(screen.getByRole('button', { name: 'Dieser Monat' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/Einträge ·/)).toHaveTextContent(
      'Oktober 2026 · 4 Einträge · Einnahmen 3.000,00 € · Ausgaben 67,10 €',
    )
  })

  it.each([
    ['Letzte 3 Monate', '/api/transactions?from=2026-08-01&to=2026-10-31', 'August bis Oktober 2026'],
    ['Dieses Jahr', '/api/transactions?from=2026-01-01&to=2026-12-31', '2026 ·'],
  ])('loads the quick choice %s', async (label, url, text) => {
    const fetchMock = renderPage()
    await screen.findByText('Kino')

    fireEvent.click(screen.getByRole('button', { name: label }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(url, expect.anything()))
    expect(screen.getByRole('button', { name: label })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText(new RegExp(text))).toBeInTheDocument()
  })

  it('loads a free period from date to date, starting with the period shown so far', async () => {
    const fetchMock = renderPage()
    await screen.findByText('Kino')

    fireEvent.click(screen.getByRole('button', { name: 'Zeitraum …' }))

    expect(screen.getByLabelText('Von')).toHaveValue('2026-10-01')
    expect(screen.getByLabelText('Bis')).toHaveValue('2026-10-31')
    fireEvent.change(screen.getByLabelText('Von'), { target: { value: '2026-09-15' } })
    fireEvent.change(screen.getByLabelText('Bis'), { target: { value: '2026-10-20' } })

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/transactions?from=2026-09-15&to=2026-10-20', expect.anything()),
    )
    expect(await screen.findByText(/15\.09\.2026 bis 20\.10\.2026/)).toBeInTheDocument()
  })

  it('waits for complete dates before loading a free period', async () => {
    const fetchMock = renderPage()
    await screen.findByText('Kino')
    fireEvent.click(screen.getByRole('button', { name: 'Zeitraum …' }))
    const calls = fetchMock.mock.calls.length

    fireEvent.change(screen.getByLabelText('Von'), { target: { value: '' } })

    expect(screen.getByText('Bitte „Von“ und „Bis“ vollständig angeben.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.length).toBe(calls)
  })

  it('filters the loaded entries by description, ignoring case', async () => {
    const fetchMock = renderPage()
    await screen.findByText('Kino')
    const calls = fetchMock.mock.calls.length

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'KIN' } })

    expect(screen.getByText('Kino')).toBeInTheDocument()
    expect(screen.queryByText('Lohn')).not.toBeInTheDocument()
    expect(screen.getByText(/1 Eintrag ·/)).toHaveTextContent('Oktober 2026 · 1 Eintrag · Einnahmen 0,00 € · Ausgaben 12,10 €')
    // Search filters what is loaded, it does not ask the server again
    expect(fetchMock.mock.calls.length).toBe(calls)
  })

  it('shows the hits of each filter option with the other filters and hides the options without one', async () => {
    renderPage()
    await screen.findByText('Kino')
    const names = (label: string) =>
      Array.from((screen.getByLabelText(label) as HTMLSelectElement).options).map((option) => option.textContent)
    expect(names('Kategorie filtern')).toEqual(['Alle Kategorien', 'Freizeit (3)', 'Gehalt (1)'])
    expect(names('Art filtern')).toEqual(['Alle Arten', 'Wiederkehrend (1)', 'Einmalig (3)'])

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'Kino' } })

    expect(names('Kategorie filtern')).toEqual(['Alle Kategorien', 'Freizeit (1)'])
    expect(names('Art filtern')).toEqual(['Alle Arten', 'Einmalig (1)'])
  })

  it('filters by category and says so when nothing matches', async () => {
    renderPage()
    await screen.findByText('Kino')

    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: '20' } })

    expect(screen.getByText('Lohn')).toBeInTheDocument()
    expect(screen.queryByText('Kino')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'Kino' } })
    expect(screen.getByText('Keine Einträge für diesen Filter.')).toBeInTheDocument()
  })

  it('separates the months with a heading and the sums of each month, newest month first', async () => {
    const september: Transaction = { kind: 'SINGLE', id: 11, recurringId: null, occurrenceDate: null, amount: -20.5, description: 'Buch', date: '2026-09-30', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null }
    renderPage([...transactions, september])

    await screen.findByText('Buch')
    const months = screen.getAllByRole('group', { name: /2026$/ })
    expect(months.map((month) => month.getAttribute('aria-label'))).toEqual(['Oktober 2026', 'September 2026'])
    expect(within(months[0]).getByRole('heading', { level: 2 })).toHaveTextContent(
      'Oktober 2026Einnahmen 3.000,00 € · Ausgaben 67,10 €',
    )
    expect(within(months[0]).queryByText('Buch')).not.toBeInTheDocument()
    expect(within(months[1]).getByText('Buch')).toBeInTheDocument()
    expect(within(months[1]).getByRole('heading', { level: 2 })).toHaveTextContent('Einnahmen 0,00 € · Ausgaben 20,50 €')
  })

  it('shows no month heading when all entries are in one month', async () => {
    renderPage()

    await screen.findByText('Kino')
    expect(screen.queryByRole('heading', { level: 2 })).not.toBeInTheDocument()
  })

  it('filters by kind: recurring or one-off entries', async () => {
    const fetchMock = renderPage()
    await screen.findByText('Kino')
    const calls = fetchMock.mock.calls.length
    const kind = screen.getByLabelText('Art filtern')

    fireEvent.change(kind, { target: { value: 'RECURRING' } })

    expect(screen.getByText('Streaming')).toBeInTheDocument()
    expect(screen.queryByText('Kino')).not.toBeInTheDocument()
    expect(screen.queryByText('Lohn')).not.toBeInTheDocument()
    expect(screen.getByText(/1 Eintrag ·/)).toHaveTextContent('Oktober 2026 · 1 Eintrag · Einnahmen 0,00 € · Ausgaben 15,00 €')

    fireEvent.change(kind, { target: { value: 'SINGLE' } })

    expect(screen.queryByText('Streaming')).not.toBeInTheDocument()
    expect(screen.getByText('Kino')).toBeInTheDocument()
    expect(screen.getByText('Lohn')).toBeInTheDocument()
    expect(screen.getByText(/3 Einträge ·/)).toHaveTextContent('3 Einträge · Einnahmen 3.000,00 € · Ausgaben 52,10 €')
    // The kind filters what is loaded, it does not ask the server again
    expect(fetchMock.mock.calls.length).toBe(calls)

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'Streaming' } })
    expect(screen.getByText('Keine Einträge für diesen Filter.')).toBeInTheDocument()
  })
})
