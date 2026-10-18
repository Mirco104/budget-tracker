import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DashboardData } from '../api/types'
import { DashboardPage } from './DashboardPage'

// Fictional values, shaped like the backend response.
const dashboard: DashboardData = {
  month: '2026-10',
  incomeTotal: 2000,
  expenses: { target: 2000, actual: 1635, difference: 365 },
  surplus: 365,
  surplusLabel: 'Monatlicher Überschuss',
  changePercent: 0.2232,
  changeLabel: 'Anstieg der Gesamteinsparung',
  categories: [
    { id: 1, name: 'Wohnen', group: 'FIXED', target: 800, actual: 800, actualYearly: 0, difference: 0 },
    { id: 2, name: 'Urlaub', group: 'FLEXIBLE', target: null, actual: 35, actualYearly: 0, difference: null },
    { id: 3, name: 'Versicherungen', group: 'FIXED', target: 60, actual: 60, actualYearly: 50, difference: 0 },
    { id: 15, name: 'Sonstiges (berechneter Rest)', group: 'REMAINDER', target: -100, actual: 0, actualYearly: 0, difference: -100 },
  ],
  income: [{ id: 20, name: 'Gehalt', actual: 2000, actualYearly: 0, share: 1 }],
  yearlyExpensesShare: 50,
  dueYearly: [],
}

function renderPage(data: DashboardData = dashboard) {
  const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(data), { status: 200 })))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DashboardPage month="2026-10" />
    </QueryClientProvider>,
  )
  return fetchMock
}

describe('DashboardPage', () => {
  it('requests the selected month and renders formatted values', async () => {
    const fetchMock = renderPage()

    expect(await screen.findByText('Monatlicher Überschuss')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-10', expect.anything())
    expect(screen.getByText('Anstieg der Gesamteinsparung')).toBeInTheDocument()
    expect(screen.getByText('22,32 %')).toBeInTheDocument()
    expect(screen.getByText('100,00 %')).toBeInTheDocument()

    const wohnen = screen.getByRole('row', { name: /Wohnen/ })
    expect(within(wohnen).getAllByText('800,00 €')).toHaveLength(2)
  })

  it('shows a dash for a missing target and marks negative values', async () => {
    renderPage()

    const urlaub = await screen.findByRole('row', { name: /Urlaub/ })
    expect(within(urlaub).getAllByText('–')).toHaveLength(2)

    const rest = screen.getByRole('row', { name: /Sonstiges/ })
    expect(within(rest).getAllByText('-100,00 €')[0]).toHaveClass('negative')
  })

  it('shows the yearly share in the actual cell and the yearly costs total', async () => {
    renderPage()

    const versicherungen = await screen.findByRole('row', { name: /Versicherungen/ })
    expect(within(versicherungen).getByText('davon 50,00 € anteilig jährlich')).toBeInTheDocument()
    const wohnen = screen.getByRole('row', { name: /Wohnen/ })
    expect(within(wohnen).queryByText(/anteilig jährlich/)).not.toBeInTheDocument()
    expect(screen.getByText('Davon anteilige Jahreskosten').nextElementSibling).toHaveTextContent('50,00 €')
  })

  it('lists yearly debits due in the month', async () => {
    renderPage({
      ...dashboard,
      dueYearly: [
        { id: 7, description: 'Kfz-Versicherung', categoryName: 'Versicherungen', amountPerYear: 600, dueDate: '2026-10-15' },
      ],
    })

    const notice = await screen.findByRole('complementary', { name: 'Fällige Jahresabbuchungen' })
    expect(within(notice).getByText('15.10.2026: Kfz-Versicherung (Versicherungen) – 600,00 €')).toBeInTheDocument()
  })

  it('shows no due notice without due debits', async () => {
    renderPage()

    await screen.findByText('Monatlicher Überschuss')
    expect(screen.queryByRole('complementary', { name: 'Fällige Jahresabbuchungen' })).not.toBeInTheDocument()
  })
})
