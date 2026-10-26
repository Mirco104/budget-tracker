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
  averageMonthlyCosts: 935,
  reserve: 20,
  series: [
    { id: 1, description: 'Miete', categoryName: 'Wohnen', amount: -900, interval: 'MONTHLY', monthlyEquivalent: 900 },
    { id: 3, description: 'Streaming', categoryName: 'Freizeit', amount: -15, interval: 'MONTHLY', monthlyEquivalent: 15 },
    { id: 2, description: 'Haftpflicht', categoryName: 'Versicherungen', amount: -240, interval: 'YEARLY', monthlyEquivalent: 20 },
  ],
  forecast: months.map((month, i): ForecastMonth =>
    i === 0
      ? {
          month,
          total: 1140,
          occurrences: [
            { recurringId: 1, description: 'Miete', date: '2026-10-01', amount: 900 },
            { recurringId: 2, description: 'Haftpflicht', date: '2026-10-15', amount: 240 },
          ],
        }
      : { month, total: 915, occurrences: [] },
  ),
}

const empty: ReserveData = {
  month: '2026-10',
  averageMonthlyCosts: 0,
  reserve: 0,
  series: [],
  forecast: months.map((month) => ({ month, total: 0, occurrences: [] })),
}

function renderPage(data: ReserveData) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify(data), { status: 200 }))))
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ReservesPage month="2026-10" />
    </QueryClientProvider>,
  )
}

describe('ReservesPage', () => {
  it('shows average monthly costs, the reserve and the active expense series', async () => {
    renderPage(reserves)

    expect((await screen.findByText('Ø monatliche Kosten')).nextElementSibling).toHaveTextContent('935,00 €')
    expect(screen.getByText('davon Rücklage').nextElementSibling).toHaveTextContent('20,00 €')

    const haftpflicht = screen.getByRole('row', { name: /^Haftpflicht/ })
    expect(within(haftpflicht).getByText('jährlich')).toBeInTheDocument()
    expect(within(haftpflicht).getByText('240,00')).toBeInTheDocument()
    expect(within(haftpflicht).getByText('20,00')).toBeInTheDocument()
  })

  it('shows a forecast of 12 months with the occurrences of each month', async () => {
    renderPage(reserves)

    const forecast = within((await screen.findByRole('heading', { name: 'Vorschau 12 Monate' })).nextElementSibling!
      .nextElementSibling as HTMLElement)
    expect(forecast.getAllByRole('row')).toHaveLength(13)
    const october = forecast.getByRole('row', { name: /Oktober 2026/ })
    expect(within(october).getByText('1.140,00')).toBeInTheDocument()
    expect(within(october).getByText('15.10.2026 Haftpflicht: 240,00 €')).toBeInTheDocument()
    expect(within(forecast.getByRole('row', { name: /September 2027/ })).getByText('915,00')).toBeInTheDocument()
  })

  it('says so when no recurring expenses run in the month', async () => {
    renderPage(empty)

    expect(await screen.findByText('In diesem Monat laufen keine wiederkehrenden Ausgaben.')).toBeInTheDocument()
    expect(screen.getByText('Ø monatliche Kosten').nextElementSibling).toHaveTextContent('0,00 €')
  })
})
