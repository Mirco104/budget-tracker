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
    recurring: 840,
    oneOff: 55,
    recurringShare: 0.42,
    oneOffShare: 0.0275,
  },
  unplanned: 1040,
  unplannedLabel: 'Nicht verplant',
  surplus: 1105,
  surplusLabel: 'Monatlicher Überschuss',
  surplusShare: 0.5525,
  categories: [
    {
      id: 1,
      name: 'Wohnen',
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
    expect(screen.getByText(/davon wiederkehrend/)).toHaveTextContent('840,00 €')
    expect(screen.getByText(/davon einmalig/)).toHaveTextContent('55,00 €')
    expect(tile('Monatlicher Überschuss')).toHaveTextContent('1.105,00 €')
    expect(screen.queryByText(/Nicht verplant/)).not.toBeInTheDocument()
  })

  it('shows one row per category without groups, a total and marks negative values', async () => {
    renderPage()

    const rows = await screen.findAllByRole('row')
    // Header, four categories and the total in the budget table, header and Gehalt in the income table
    expect(rows.map((row) => row.firstElementChild?.textContent)).toEqual([
      'Kategorie',
      'Wohnen',
      'Versicherungen',
      'Freizeit',
      'Urlaub',
      'Gesamt',
      'Kategorie',
      'Gehalt',
    ])
    expect(screen.queryByRole('button', { name: /Fixkosten/ })).not.toBeInTheDocument()

    const versicherungen = screen.getByRole('row', { name: /Versicherungen/ })
    expect(within(versicherungen).getByText('-20,00')).toHaveClass('negative')

    const urlaub = screen.getByRole('row', { name: /Urlaub/ })
    expect(within(urlaub).getAllByText('–')).toHaveLength(2)

    const total = screen.getByRole('row', { name: /Gesamt/ })
    expect(within(total).getByText('960,00')).toBeInTheDocument()
    expect(within(total).getByText('65,00')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Soll (€)' })).toBeInTheDocument()
  })

  it('switches all values to shares of the income', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '%' }))

    expect(screen.getByRole('button', { name: '%' })).toHaveAttribute('aria-pressed', 'true')
    expect(tile('Einnahmen')).toHaveTextContent('100,00 %')
    expect(tile('Ausgaben')).toHaveTextContent('44,75 %')
    expect(screen.getByText(/davon wiederkehrend/)).toHaveTextContent('42,00 %')
    expect(screen.getByText(/davon einmalig/)).toHaveTextContent('2,75 %')
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
      expenses: {
        ...dashboard.expenses,
        targetShare: null,
        actualShare: null,
        differenceShare: null,
        recurringShare: null,
        oneOffShare: null,
      },
      income: [],
    })

    fireEvent.click(await screen.findByRole('button', { name: '%' }))

    expect(tile('Einnahmen')).toHaveTextContent('–')
    expect(tile('Monatlicher Verlust')).toHaveTextContent('–')
    expect(screen.getByText(/davon wiederkehrend/)).toHaveTextContent('–')
    expect(screen.getByText('In diesem Monat gibt es keine Einnahmen.')).toBeInTheDocument()
  })

  it('shows an expense in an income category without target', async () => {
    renderPage({
      ...dashboard,
      categories: [
        ...dashboard.categories,
        {
          id: 21,
          name: 'Nebenjob',
          target: null,
          actual: 20,
          difference: null,
          targetShare: null,
          actualShare: 0.01,
          differenceShare: null,
        },
      ],
    })

    const nebenjob = await screen.findByRole('row', { name: /Nebenjob/ })
    expect(within(nebenjob).getByText('20,00')).toBeInTheDocument()
    expect(within(nebenjob).getAllByText('–')).toHaveLength(2)
  })

  it('says so when there is nothing to show', async () => {
    renderPage({ ...dashboard, categories: [] })

    expect(await screen.findByText('In diesem Monat gibt es weder Soll-Werte noch Ausgaben.')).toBeInTheDocument()
  })
})
