import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DashboardData } from '../api/types'
import { DashboardPage } from './DashboardPage'

// Fictional values, shaped like the backend response.
const dashboard: DashboardData = {
  month: '2026-10',
  incomeTotal: 2000,
  expenses: { target: 2100, actual: 1635, difference: 465 },
  unplanned: -100,
  surplus: 365,
  surplusLabel: 'Monatlicher Überschuss',
  savingsRate: 0.1825,
  groups: [
    { group: 'FIXED', target: 860, actual: 860, difference: 0 },
    { group: 'FLEXIBLE', target: 0, actual: 35, difference: -35 },
    { group: 'SAVINGS', target: 0, actual: 0, difference: 0 },
  ],
  categories: [
    { id: 1, name: 'Wohnen', group: 'FIXED', target: 800, actual: 800, difference: 0 },
    { id: 2, name: 'Urlaub', group: 'FLEXIBLE', target: null, actual: 35, difference: null },
    { id: 3, name: 'Versicherungen', group: 'FIXED', target: 60, actual: 60, difference: -20 },
  ],
  income: [{ id: 20, name: 'Gehalt', actual: 2000, share: 1 }],
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
    expect(screen.getByText('365,00 € (18,25 %)')).toBeInTheDocument()
    expect(screen.getByText('100,00 %')).toBeInTheDocument()
    expect(screen.getByText('Abweichung zum Soll').nextSibling).toHaveTextContent('465,00 €')
    expect(screen.getByText('Nicht verplant').nextSibling).toHaveTextContent('-100,00 €')
    expect(screen.getByText('Nicht verplant').nextSibling).toHaveClass('negative')

    const wohnen = screen.getByRole('row', { name: /Wohnen/ })
    expect(within(wohnen).getAllByText('800,00 €')).toHaveLength(2)
  })

  it('shows a dash for a missing target and marks negative values', async () => {
    renderPage()

    const urlaub = await screen.findByRole('row', { name: /Urlaub/ })
    expect(within(urlaub).getAllByText('–')).toHaveLength(2)

    const versicherungen = screen.getByRole('row', { name: /Versicherungen/ })
    expect(within(versicherungen).getByText('-20,00 €')).toHaveClass('negative')
  })

  it('shows group totals with labels and recommendations', async () => {
    renderPage()

    const fixed = await screen.findByRole('row', { name: /Fixkosten/ })
    expect(within(fixed).getByText('ca. 50 %')).toBeInTheDocument()
    expect(within(fixed).getAllByText('860,00 €')).toHaveLength(2)

    const flexible = screen.getByRole('row', { name: /Flexible Ausgaben/ })
    expect(within(flexible).getByText('-35,00 €')).toHaveClass('negative')
    expect(screen.queryByRole('row', { name: /^Ohne Gruppe/ })).not.toBeInTheDocument()
  })

  it('lists an ungrouped expense with its own group line and marks negative income', async () => {
    renderPage({
      ...dashboard,
      groups: [...dashboard.groups, { group: null, target: 0, actual: 20, difference: -20 }],
      categories: [
        ...dashboard.categories,
        { id: 21, name: 'Nebenjob', group: null, target: null, actual: 20, difference: null },
      ],
      income: [...dashboard.income, { id: 21, name: 'Nebenjob', actual: -20, share: 0 }],
    })

    const rows = await screen.findAllByRole('row', { name: /Nebenjob/ })
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByText('20,00 €')).toBeInTheDocument()
    expect(within(rows[1]).getByText('-20,00 €')).toHaveClass('negative')

    const ungrouped = screen.getByRole('row', { name: /^Ohne Gruppe/ })
    expect(within(ungrouped).queryByText(/ca\./)).not.toBeInTheDocument()
    expect(within(ungrouped).getByText('-20,00 €')).toHaveClass('negative')
  })
})
