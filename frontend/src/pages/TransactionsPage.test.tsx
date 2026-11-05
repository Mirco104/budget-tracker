import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Category, RecurringTransaction, ReserveData, Transaction } from '../api/types'
import { TransactionsPage } from './TransactionsPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 6, name: 'Freizeit', icon: 'music', sortOrder: 6, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt', icon: 'wallet', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: true },
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
      <TransactionsPage month="2026-10" />
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

  it('says so when the month has no entries', async () => {
    renderPage([])

    expect(await screen.findByText('Keine Transaktionen in diesem Monat.')).toBeInTheDocument()
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
})
