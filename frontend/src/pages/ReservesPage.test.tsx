import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ForecastMonth, ReserveData } from '../api/types'
import { ReservesPage } from './ReservesPage'

const months = ['2026-10', '2026-11', '2026-12', '2027-01', '2027-02', '2027-03',
  '2027-04', '2027-05', '2027-06', '2027-07', '2027-08', '2027-09']

// Fictional values of fixture F1 (spec section 12), shaped like the backend response.
const reserves: ReserveData = {
  month: '2026-10',
  averageExpenses: 1026,
  averageMonths: 9,
  monthlyReserve: 20,
  reserveBalance: 240,
  reserves: [
    { recurringId: 2, description: 'Haftpflicht', categoryName: 'Versicherungen', amount: 240, interval: 'YEARLY', nextDate: '2026-10-15', monthly: 20, saved: 240 },
  ],
  forecast: months.map((month, i): ForecastMonth =>
    i === 0
      ? {
          month,
          total: 1340,
          entries: [
            { kind: 'RECURRING', id: null, recurringId: 1, description: 'Miete', date: '2026-10-01', amount: 900 },
            { kind: 'SINGLE', id: 7, recurringId: null, description: 'Strom', date: '2026-10-02', amount: 200 },
            { kind: 'RECURRING', id: null, recurringId: 2, description: 'Haftpflicht', date: '2026-10-15', amount: 240 },
          ],
        }
      : { month, total: 915, entries: [] },
  ),
}

const empty: ReserveData = {
  month: '2026-10',
  averageExpenses: null,
  averageMonths: 0,
  monthlyReserve: 0,
  reserveBalance: 0,
  reserves: [],
  forecast: months.map((month) => ({ month, total: 0, entries: [] })),
}

function renderPage(data: ReserveData) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify(data), { status: 200 }))))
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ReservesPage month="2026-10" />
    </QueryClientProvider>,
  )
}

function tile(label: RegExp | string) {
  return screen.getByText(label, { selector: 'dt' }).nextElementSibling
}

describe('ReservesPage', () => {
  it('shows the average expenses and how many months they cover', async () => {
    renderPage(reserves)

    await screen.findByRole('heading', { name: 'Auswertung' })
    expect(tile(/Ø Ausgaben pro Monat/)).toHaveTextContent('1.026,00 €')
    expect(screen.getByText(/letzte 9 Monate/)).toBeInTheDocument()
    expect(screen.getByText('Es gibt erst seit 9 Monaten Einträge, daher zählen nur diese.')).toBeInTheDocument()
  })

  it('shows the reserve pot with what to put aside and what should be saved by now', async () => {
    renderPage(reserves)

    await screen.findByRole('heading', { name: 'Rücklagen' })
    expect(tile('Monatlich zurücklegen')).toHaveTextContent('20,00 €')
    expect(tile('Sollte jetzt angespart sein')).toHaveTextContent('240,00 €')
    expect(
      screen.getByText('Lege jeden Monat 20,00 € zur Seite, dann ist jede seltene Abbuchung gedeckt, wenn sie fällig wird.'),
    ).toBeInTheDocument()
    const haftpflicht = screen.getByRole('row', { name: /^Haftpflicht/ })
    expect(within(haftpflicht).getByText('15.10.2026')).toBeInTheDocument()
    expect(within(haftpflicht).getByText('20,00')).toBeInTheDocument()
    expect(within(haftpflicht).getAllByText('240,00')).toHaveLength(2)
  })

  it('shows a forecast of 12 months with single and recurring expenses', async () => {
    renderPage(reserves)

    const table = (await screen.findByRole('columnheader', { name: 'Ausgaben (€)' })).closest('table') as HTMLElement
    expect(within(table).getAllByRole('row')).toHaveLength(13)
    const october = within(table).getByRole('row', { name: /Oktober 2026/ })
    expect(within(october).getByText('1.340,00')).toBeInTheDocument()
    expect(within(october).getByText('02.10.2026 Strom: 200,00 €')).not.toHaveTextContent('wiederkehrend')
    expect(within(october).getByText(/01\.10\.2026 Miete/)).toHaveTextContent('wiederkehrend')
  })

  it('explains an empty analysis without a completed month and without rare expenses', async () => {
    renderPage(empty)

    await screen.findByRole('heading', { name: 'Auswertung' })
    expect(tile(/Ø Ausgaben pro Monat/)).toHaveTextContent('–')
    expect(screen.getByText(/Noch kein abgeschlossener Monat mit Einträgen/)).toBeInTheDocument()
    expect(screen.getByText(/Keine seltenen Ausgaben in den nächsten 12 Monaten/)).toBeInTheDocument()
    expect(screen.queryByText('Monatlich zurücklegen')).not.toBeInTheDocument()
  })
})
