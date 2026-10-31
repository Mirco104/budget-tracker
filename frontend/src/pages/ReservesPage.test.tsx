import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ForecastMonth, HistoryMonth, ReserveData } from '../api/types'
import { ReservesPage } from './ReservesPage'

const months = ['2026-10', '2026-11', '2026-12', '2027-01', '2027-02', '2027-03',
  '2027-04', '2027-05', '2027-06', '2027-07', '2027-08', '2027-09']

// Without income there are no shares (R14)
const noShares = { incomeShare: null, expensesShare: null, surplusShare: null }

// Fictional values of fixture F1 (spec section 12), shaped like the backend response.
// January to August with rent and streaming, September additionally with groceries (fixture F1).
const history: HistoryMonth[] = ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07',
  '2026-08', '2026-09'].map((month) =>
  month === '2026-09'
    ? {
        month,
        income: 0,
        expenses: 1914,
        surplus: -1914,
        ...noShares,
        incomeCategories: [],
        expenseCategories: [
          { name: 'Wohnen', amount: 900, share: null },
          { name: 'Lebensmittel', amount: 999, share: null },
          { name: 'Freizeit', amount: 15, share: null },
        ],
      }
    : { month, income: 0, expenses: 915, surplus: -915, ...noShares, incomeCategories: [], expenseCategories: [] },
)

const reserves: ReserveData = {
  month: '2026-10',
  averageIncome: 0,
  averageExpenses: 1026,
  averageSurplus: -1026,
  averageMonths: 9,
  history,
  totalIncome: 0,
  totalExpenses: 9234,
  totalSurplus: -9234,
  ...noShares,
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
  averageIncome: null,
  averageExpenses: null,
  averageSurplus: null,
  averageMonths: 0,
  history: [],
  totalIncome: null,
  totalExpenses: null,
  totalSurplus: null,
  ...noShares,
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

function renderWithContainer(data: ReserveData) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify(data), { status: 200 }))))
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ReservesPage month="2026-10" />
    </QueryClientProvider>,
  )
}

function tile(label: RegExp | string) {
  return screen.getByText(label, { selector: 'dt' }).nextElementSibling
}

describe('ReservesPage', () => {
  beforeEach(() => localStorage.clear())

  it('shows the average expenses and how many months they cover', async () => {
    renderPage(reserves)

    await screen.findByRole('heading', { name: 'Auswertung' })
    expect(tile(/Ø Ausgaben pro Monat/)).toHaveTextContent('1.026,00 €')
    expect(screen.getByText(/letzte 9 Monate/)).toBeInTheDocument()
    expect(screen.getByText('Es gibt erst seit 9 Monaten Einträge, daher zählen nur diese.')).toBeInTheDocument()
  })

  it('looks back on income, expenses and surplus of the months of the average', async () => {
    const { container } = renderWithContainer(reserves)

    const table = within(await screen.findByRole('table', { name: 'Rückblick' }))
    // Header, 9 months, average, total
    expect(table.getAllByRole('row')).toHaveLength(12)
    const september = table.getByRole('row', { name: /September 2026/ })
    expect(within(september).getByText('1.914,00')).toBeInTheDocument()
    expect(within(september).getByText('-1.914,00')).toHaveClass('negative')
    const average = table.getByRole('row', { name: /^Ø/ })
    expect(within(average).getByText('1.026,00')).toBeInTheDocument()
    expect(within(average).getByText('-1.026,00')).toHaveClass('negative')

    expect(table.queryByRole('row', { name: /Lebensmittel/ })).not.toBeInTheDocument()
    fireEvent.click(within(september).getByRole('button', { name: /September 2026/ }))
    expect(within(table.getByRole('row', { name: /Lebensmittel/ })).getByText('999,00')).toBeInTheDocument()
    // The look back follows the average tile, before the reserve pot
    const headings = Array.from(container.querySelectorAll('dt, h3')).map((el) => el.textContent ?? '')
    const lookBack = headings.findIndex((t) => t.endsWith('Rückblick'))
    expect(lookBack).toBeGreaterThan(headings.findIndex((t) => t.startsWith('Ø Ausgaben')))
    expect(lookBack).toBeLessThan(headings.indexOf('Rücklagen'))
  })

  it('loads the average and look back for the chosen period', async () => {
    const sixMonths: ReserveData = { ...reserves, averageMonths: 6, history: history.slice(3) }
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(new Response(JSON.stringify(url.includes('months=6') ? sixMonths : reserves), { status: 200 })),
    )
    vi.stubGlobal('fetch', fetchMock)
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ReservesPage month="2026-10" />
      </QueryClientProvider>,
    )

    const select = await screen.findByRole('combobox', { name: 'Zeitraum' })
    expect(select).toHaveValue('12')
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      '3 Monate', '6 Monate', '12 Monate', '24 Monate',
    ])
    expect(fetchMock).toHaveBeenLastCalledWith('/api/reserves?month=2026-10&months=12', expect.anything())

    fireEvent.change(select, { target: { value: '6' } })

    expect(await screen.findByText(/letzte 6 Monate/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/reserves?month=2026-10&months=6', expect.anything())
    // Six months with entries fill the chosen period, so there is no hint about fewer months
    expect(screen.queryByText(/daher zählen nur diese/)).not.toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Rückblick' })).getAllByRole('row')).toHaveLength(9)
  })

  it('shows a chart of income and expenses above the look back', async () => {
    renderPage(reserves)

    const chart = await screen.findByRole('img', { name: 'Diagramm Einnahmen und Ausgaben je Monat' })
    const table = screen.getByRole('table', { name: 'Rückblick' })
    expect(chart.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /Rückblick/ }))
    expect(screen.queryByRole('img', { name: /Diagramm/ })).not.toBeInTheDocument()
  })

  it('sums the months of the look back in a total row', async () => {
    renderPage(reserves)

    const table = within(await screen.findByRole('table', { name: 'Rückblick' }))
    const total = table.getByRole('row', { name: /^Gesamt/ })
    expect(within(total).getByText('9.234,00')).toBeInTheDocument()
    expect(within(total).getByText('-9.234,00')).toHaveClass('negative')
  })

  it('switches the look back to shares of the income of each month', async () => {
    const withIncome: ReserveData = {
      ...reserves,
      history: [
        {
          month: '2026-09',
          income: 3000,
          expenses: 1000,
          surplus: 2000,
          incomeShare: 1,
          expensesShare: 0.3333,
          surplusShare: 0.6667,
          incomeCategories: [{ name: 'Gehalt/Lohn (netto)', amount: 3000, share: 1 }],
          expenseCategories: [{ name: 'Wohnen', amount: 900, share: 0.3 }],
        },
        { month: '2026-08', income: 0, expenses: 500, surplus: -500, ...noShares, incomeCategories: [], expenseCategories: [] },
      ],
      totalIncome: 3000,
      totalExpenses: 1500,
      totalSurplus: 1500,
      incomeShare: 1,
      expensesShare: 0.5,
      surplusShare: 0.5,
    }
    renderPage(withIncome)

    const table = within(await screen.findByRole('table', { name: 'Rückblick' }))
    fireEvent.click(screen.getByRole('button', { name: '%' }))

    expect(screen.getByRole('button', { name: '%' })).toHaveAttribute('aria-pressed', 'true')
    expect(table.getByRole('columnheader', { name: 'Ausgaben (%)' })).toBeInTheDocument()
    const september = table.getByRole('row', { name: /September 2026/ })
    expect(within(september).getByText('100,00')).toBeInTheDocument()
    expect(within(september).getByText('33,33')).toBeInTheDocument()
    expect(within(september).getByText('66,67')).toHaveClass('positive')
    // Without income in August the shares are empty
    const august = within(table.getByRole('row', { name: /August 2026/ })).getAllByText('–')
    expect(august).toHaveLength(3)
    expect(august[2]).not.toHaveClass('negative')
    expect(within(table.getByRole('row', { name: /^Gesamt/ })).getAllByText('50,00')).toHaveLength(2)

    fireEvent.click(within(september).getByRole('button', { name: /September 2026/ }))
    expect(within(table.getByRole('row', { name: /Wohnen/ })).getByText('30,00')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '€' }))
    expect(within(september).getByText('1.000,00')).toBeInTheDocument()
  })

  it('collapses look back and forecast and remembers it', async () => {
    renderPage(reserves)

    const lookBack = await screen.findByRole('button', { name: /Rückblick/ })
    expect(lookBack).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(lookBack)
    fireEvent.click(screen.getByRole('button', { name: /Vorschau 12 Monate/ }))

    expect(screen.queryByRole('table', { name: 'Rückblick' })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Anzeige' })).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Vorschau 12 Monate' })).not.toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()

    cleanup()
    renderPage(reserves)
    expect(await screen.findByRole('button', { name: /Rückblick/ })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('button', { name: /Vorschau 12 Monate/ })).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(screen.getByRole('button', { name: /Rückblick/ }))
    expect(screen.getByRole('table', { name: 'Rückblick' })).toBeInTheDocument()
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

    const table = await screen.findByRole('table', { name: 'Vorschau 12 Monate' })
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
    expect(screen.queryByRole('table', { name: 'Rückblick' })).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /Diagramm/ })).not.toBeInTheDocument()
  })
})
