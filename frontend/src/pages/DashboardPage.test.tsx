import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DashboardData } from '../api/types'
import { DashboardPage } from './DashboardPage'

// Fictional values, shaped like the backend response.
const dashboard: DashboardData = {
  month: '2026-10',
  incomeTotal: 2000,
  incomeTotalShare: 1,
  expenses: {
    target: 960,
    actual: 895,
    difference: 65,
    targetShare: 0.48,
    actualShare: 0.4475,
    differenceShare: 0.0325,
  },
  unplanned: 1040,
  unplannedLabel: 'Nicht verplant',
  surplus: 1105,
  surplusLabel: 'Monatlicher Überschuss',
  surplusShare: 0.5525,
  groups: [
    { group: 'FIXED', target: 860, actual: 860, difference: 0, targetShare: 0.43, actualShare: 0.43, differenceShare: 0 },
    {
      group: 'FLEXIBLE',
      target: 100,
      actual: 35,
      difference: 65,
      targetShare: 0.05,
      actualShare: 0.0175,
      differenceShare: 0.0325,
    },
  ],
  categories: [
    {
      id: 1,
      name: 'Wohnen',
      group: 'FIXED',
      target: 800,
      actual: 780,
      difference: 20,
      targetShare: 0.4,
      actualShare: 0.39,
      differenceShare: 0.01,
    },
    {
      id: 3,
      name: 'Versicherungen',
      group: 'FIXED',
      target: 60,
      actual: 80,
      difference: -20,
      targetShare: 0.03,
      actualShare: 0.04,
      differenceShare: -0.01,
    },
    {
      id: 8,
      name: 'Freizeit',
      group: 'FLEXIBLE',
      target: 100,
      actual: 0,
      difference: 100,
      targetShare: 0.05,
      actualShare: 0,
      differenceShare: 0.05,
    },
    {
      id: 9,
      name: 'Urlaub',
      group: 'FLEXIBLE',
      target: null,
      actual: 35,
      difference: null,
      targetShare: null,
      actualShare: 0.0175,
      differenceShare: null,
    },
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

function tile(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextSibling as HTMLElement
}

describe('DashboardPage', () => {
  it('requests the selected month and shows income, expenses and surplus', async () => {
    const fetchMock = renderPage()

    expect(await screen.findByText('Monatlicher Überschuss')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-10', expect.anything())
    expect(tile('Einnahmen')).toHaveTextContent('2.000,00 €')
    expect(tile('Ausgaben')).toHaveTextContent('895,00 €')
    expect(tile('Monatlicher Überschuss')).toHaveTextContent('1.105,00 €')
    expect(screen.queryByText(/Nicht verplant/)).not.toBeInTheDocument()
  })

  it('shows groups with their categories, a total and marks negative values', async () => {
    renderPage()

    const fixed = await screen.findByRole('row', { name: /Fixkosten/ })
    expect(within(fixed).getAllByText('860,00')).toHaveLength(2)

    const versicherungen = screen.getByRole('row', { name: /Versicherungen/ })
    expect(within(versicherungen).getByText('-20,00')).toHaveClass('negative')

    const urlaub = screen.getByRole('row', { name: /Urlaub/ })
    expect(within(urlaub).getAllByText('–')).toHaveLength(2)

    const total = screen.getByRole('row', { name: /Gesamt/ })
    expect(within(total).getByText('960,00')).toBeInTheDocument()
    expect(within(total).getByText('65,00')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Soll (€)' })).toBeInTheDocument()
  })

  it('collapses and expands a group', async () => {
    renderPage()

    const toggle = await screen.findByRole('button', { name: /Fixkosten/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('row', { name: /Wohnen/ })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Fixkosten/ })).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Freizeit/ })).toBeInTheDocument()

    fireEvent.click(toggle)
    expect(screen.getByRole('row', { name: /Wohnen/ })).toBeInTheDocument()
  })

  it('switches all values to shares of the income', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '%' }))

    expect(screen.getByRole('button', { name: '%' })).toHaveAttribute('aria-pressed', 'true')
    expect(tile('Einnahmen')).toHaveTextContent('100,00 %')
    expect(tile('Ausgaben')).toHaveTextContent('44,75 %')
    expect(tile('Monatlicher Überschuss')).toHaveTextContent('55,25 %')
    expect(screen.getByRole('columnheader', { name: 'Soll (%)' })).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Wohnen/ })).getByText('39,00')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Versicherungen/ })).getByText('-1,00')).toHaveClass('negative')
    expect(within(screen.getByRole('row', { name: /Gehalt/ })).getByText('100,00')).toBeInTheDocument()
    expect(screen.queryByText(/2\.000,00/)).not.toBeInTheDocument()
  })

  it('shows dashes for shares without income', async () => {
    renderPage({
      ...dashboard,
      incomeTotal: 0,
      incomeTotalShare: null,
      surplus: -895,
      surplusLabel: 'Monatlicher Verlust',
      surplusShare: null,
      expenses: { ...dashboard.expenses, targetShare: null, actualShare: null, differenceShare: null },
      income: [],
    })

    fireEvent.click(await screen.findByRole('button', { name: '%' }))

    expect(tile('Einnahmen')).toHaveTextContent('–')
    expect(tile('Monatlicher Verlust')).toHaveTextContent('–')
    expect(screen.getByText('In diesem Monat gibt es keine Einnahmen.')).toBeInTheDocument()
  })

  it('shows ungrouped expenses as their own group', async () => {
    renderPage({
      ...dashboard,
      groups: [
        ...dashboard.groups,
        { group: null, target: 0, actual: 20, difference: -20, targetShare: 0, actualShare: 0.01, differenceShare: -0.01 },
      ],
      categories: [
        ...dashboard.categories,
        {
          id: 21,
          name: 'Nebenjob',
          group: null,
          target: null,
          actual: 20,
          difference: null,
          targetShare: null,
          actualShare: 0.01,
          differenceShare: null,
        },
      ],
    })

    const ungrouped = await screen.findByRole('row', { name: /Ohne Gruppe/ })
    expect(within(ungrouped).getByText('-20,00')).toHaveClass('negative')
    expect(within(screen.getByRole('row', { name: /Nebenjob/ })).getByText('20,00')).toBeInTheDocument()
  })

  it('says so when there is nothing to show', async () => {
    renderPage({ ...dashboard, groups: [], categories: [] })

    expect(await screen.findByText('In diesem Monat gibt es weder Soll-Werte noch Ausgaben.')).toBeInTheDocument()
  })
})
