import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Category, RecurringTransaction, ReserveData, Transaction } from '../api/types'
import { TransactionsPage } from './TransactionsPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 6, name: 'Freizeit', icon: 'music', sortOrder: 6, targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt', icon: 'wallet', sortOrder: 20, targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: true },
]

const transactions: Transaction[] = [
  { kind: 'RECURRING', id: null, recurringId: 3, occurrenceDate: '2026-10-05', amount: -15, description: 'Streaming', date: '2026-10-05', categoryId: 6, categoryName: 'Freizeit' },
  { kind: 'SINGLE', id: 8, recurringId: null, occurrenceDate: null, amount: 3000, description: 'Lohn', date: '2026-10-20', categoryId: 20, categoryName: 'Gehalt' },
  { kind: 'SINGLE', id: 7, recurringId: null, occurrenceDate: null, amount: -12.1, description: 'Kino', date: '2026-10-20', categoryId: 6, categoryName: 'Freizeit' },
  { kind: 'SINGLE', id: 9, recurringId: null, occurrenceDate: null, amount: -40, description: 'Konzert', date: '2026-10-28', categoryId: 6, categoryName: 'Freizeit' },
]

const series: RecurringTransaction[] = [
  { id: 3, seriesId: 'a1', amount: -15, description: 'Streaming', categoryId: 6, categoryName: 'Freizeit', interval: 'MONTHLY', startDate: '2026-01-05', endDate: null, monthlyEquivalent: 15 },
]

// Only the fields the reserve summary uses.
const reserves: Pick<ReserveData, 'monthlyReserve' | 'reserveBalance' | 'reserves'> = {
  monthlyReserve: 20,
  reserveBalance: 100,
  reserves: [
    { recurringId: 4, description: 'Haftpflicht', categoryName: 'Versicherungen', amount: 240, interval: 'YEARLY', nextDate: '2027-10-15', monthly: 20, saved: 100 },
  ],
}

function renderPage(entries: Transaction[] = transactions) {
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
    const september: Transaction = { kind: 'SINGLE', id: 11, recurringId: null, occurrenceDate: null, amount: -20.5, description: 'Buch', date: '2026-09-30', categoryId: 6, categoryName: 'Freizeit' }
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
