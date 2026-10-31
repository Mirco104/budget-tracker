import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category, RecurringTransaction, ReserveData, Transaction } from '../api/types'
import { TransactionsPage } from './TransactionsPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 6, name: 'Freizeit', sortOrder: 6, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false },
]

const transactions: Transaction[] = [
  { kind: 'SINGLE', id: 7, recurringId: null, occurrenceDate: null, amount: -12, description: 'Kino', date: '2026-10-20', categoryId: 6, categoryName: 'Freizeit' },
  { kind: 'RECURRING', id: null, recurringId: 3, occurrenceDate: '2026-10-05', amount: -15, description: 'Streaming', date: '2026-10-05', categoryId: 6, categoryName: 'Freizeit' },
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

function renderPage() {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const body = init?.method && init.method !== 'GET'
      ? null
      : url.startsWith('/api/transactions')
        ? transactions
        : url.startsWith('/api/categories')
          ? categories
          : url.startsWith('/api/reserves')
            ? reserves
            : series
    return Promise.resolve(
      init?.method === 'DELETE' ? new Response(null, { status: 204 }) : new Response(JSON.stringify(body ?? {}), { status: 200 }),
    )
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TransactionsPage month="2026-10" />
    </QueryClientProvider>,
  )
  return fetchMock
}

async function streamingRow() {
  return screen.findByRole('row', { name: /Streaming/ })
}

describe('TransactionsPage', () => {
  it('marks occurrences of recurring transactions', async () => {
    renderPage()

    expect(within(await streamingRow()).getByText('wiederkehrend')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Kino/ })).queryByText('wiederkehrend')).not.toBeInTheDocument()
  })

  it('shows the monthly reserve and what should be saved by now', async () => {
    renderPage()

    expect(
      await screen.findByText('Rücklagen: monatlich 20,00 € zurücklegen, jetzt angespart 100,00 €.'),
    ).toBeInTheDocument()
  })

  it('colors expenses red, shows the category without Ausgabe/Einnahme', async () => {
    renderPage()

    const kino = await screen.findByRole('row', { name: /Kino/ })
    expect(within(kino).getByText('-12,00 €')).toHaveClass('negative')
    expect(within(kino).getByText('Freizeit')).toBeInTheDocument()
    expect(within(kino).queryByText(/Ausgabe/)).not.toBeInTheDocument()
  })

  it('asks for the scope before editing an occurrence and edits the following ones as a series', async () => {
    const fetchMock = renderPage()
    await screen.findByRole('option', { name: 'Freizeit' })

    fireEvent.click(within(await streamingRow()).getByRole('button', { name: 'Bearbeiten' }))

    const dialog = screen.getByRole('dialog', { name: '„Streaming“ vom 05.10.2026 bearbeiten' })
    expect(within(dialog).queryByRole('button', { name: /Alle/ })).not.toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Dieser und alle folgenden' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '„Streaming“ ab 05.10.2026 bearbeiten' })).toBeInTheDocument()
    expect(screen.getByLabelText('Datum (erste Abbuchung)')).toHaveValue('2026-10-05')
    expect(screen.getByLabelText('Betrag (€)')).toHaveValue('15,00')

    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '18' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/recurring-transactions/3/occurrences/2026-10-05?scope=FOLLOWING',
        expect.objectContaining({ method: 'PUT' }),
      ),
    )
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(String(put?.[1]?.body))).toEqual({
      amount: -18,
      description: 'Streaming',
      categoryId: 6,
      interval: 'MONTHLY',
      startDate: '2026-10-05',
      endDate: null,
    })
  })

  it('edits only this occurrence with the transaction form', async () => {
    renderPage()
    await screen.findByRole('option', { name: 'Freizeit' })

    fireEvent.click(within(await streamingRow()).getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.click(screen.getByRole('button', { name: 'Nur dieser Eintrag' }))

    expect(screen.getByRole('heading', { name: 'Nur den Eintrag vom 05.10.2026 bearbeiten' })).toBeInTheDocument()
    expect(screen.getByLabelText('Datum')).toHaveValue('2026-10-05')
    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
  })

  it('creates a series from the transaction form when a repeat is chosen', async () => {
    const fetchMock = renderPage()
    await screen.findByRole('option', { name: 'Freizeit' })

    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '9,99' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Musik' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '6' } })
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/recurring-transactions', expect.objectContaining({ method: 'POST' })),
    )
    expect(fetchMock).not.toHaveBeenCalledWith('/api/transactions', expect.objectContaining({ method: 'POST' }))
  })

  it('has no separate list of series', async () => {
    renderPage()

    await streamingRow()
    expect(screen.queryByText(/Wiederkehrende Transaktionen/)).not.toBeInTheDocument()
  })

  it('deletes only this occurrence after asking for the scope', async () => {
    const fetchMock = renderPage()

    fireEvent.click(within(await streamingRow()).getByRole('button', { name: 'Löschen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Nur dieser Eintrag' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/recurring-transactions/3/occurrences/2026-10-05?scope=THIS',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })
})
