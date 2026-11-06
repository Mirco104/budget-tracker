import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ForecastMonth, HistoryData, HistoryMonth, ReserveData } from '../api/types'
import { EvaluationPage } from './EvaluationPage'

const forecastMonths = ['2026-11', '2026-12', '2027-01', '2027-02', '2027-03', '2027-04',
  '2027-05', '2027-06', '2027-07', '2027-08', '2027-09', '2027-10']

// Fictional values of fixture F1 (spec section 12), shaped like the backend response.
const salary = { id: 15, name: 'Gehalt/Lohn (netto)', icon: 'wallet', amount: 3000 }
const months: HistoryMonth[] = ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07',
  '2026-08', '2026-09'].map((month) =>
  month === '2026-09'
    ? {
        month,
        income: 3000,
        expenses: 1914,
        surplus: 1086,
        incomeCategories: [salary],
        expenseCategories: [
          { id: 1, name: 'Wohnen', icon: 'home', amount: 900 },
          { id: 7, name: 'Lebensmittel', icon: 'cart', amount: 999 },
          { id: 8, name: 'Freizeit', icon: 'music', amount: 15 },
        ],
      }
    : { month, income: 3000, expenses: 915, surplus: 2085, incomeCategories: [], expenseCategories: [] },
)

const history: HistoryData = {
  from: '2025-11',
  to: '2026-10',
  months: 9,
  averageIncome: 3000,
  averageExpenses: 1026,
  averageSurplus: 1974,
  total: { income: 27000, expenses: 9234, surplus: 17766 },
  history: months,
}

const emptyHistory: HistoryData = {
  from: '2025-11',
  to: '2026-10',
  months: 0,
  averageIncome: null,
  averageExpenses: null,
  averageSurplus: null,
  total: null,
  history: [],
}

const outlook: ReserveData = {
  month: '2026-11',
  monthlyReserve: 20,
  reserveBalance: 20,
  reserves: [
    { recurringId: 2, description: 'Haftpflicht', categoryName: 'Versicherungen', amount: 240, interval: 'YEARLY', nextDate: '2027-10-15', monthly: 20, saved: 20 },
  ],
  potMonths: forecastMonths.map((month, i) => ({ month, balance: 20 * (i + 1), withdrawal: i === 11 ? 240 : 0 })),
  forecast: forecastMonths.map((month, i): ForecastMonth =>
    i === 0
      ? {
          month,
          total: 1100,
          entries: [
            { kind: 'RECURRING', id: null, recurringId: 1, description: 'Miete', date: '2026-11-01', amount: 900 },
            { kind: 'SINGLE', id: 7, recurringId: null, description: 'Strom', date: '2026-11-02', amount: 200 },
          ],
        }
      : { month, total: 915, entries: [] },
  ),
}

const emptyOutlook: ReserveData = {
  month: '2026-11',
  monthlyReserve: 0,
  reserveBalance: 0,
  reserves: [],
  potMonths: forecastMonths.map((month) => ({ month, balance: 0, withdrawal: 0 })),
  forecast: forecastMonths.map((month) => ({ month, total: 0, entries: [] })),
}

function renderPage(historyData: HistoryData | (() => Promise<Response>) = history, reserveData = outlook) {
  const fetchMock = vi.fn((url: string) => {
    if (url.startsWith('/api/history')) {
      return typeof historyData === 'function'
        ? historyData()
        : Promise.resolve(new Response(JSON.stringify(historyData), { status: 200 }))
    }
    return Promise.resolve(new Response(JSON.stringify(reserveData), { status: 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <EvaluationPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

function tile(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextSibling as HTMLElement
}

describe('EvaluationPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 5))
    window.location.hash = '#/auswertung'
  })

  afterEach(() => {
    vi.useRealTimers()
    window.location.hash = ''
  })

  // Decision 29: two sub pages with their own address (US29)
  it('has the sub pages Rückblick and Vorausschau with their own addresses', async () => {
    renderPage()

    const nav = screen.getByRole('navigation', { name: 'Auswertung' })
    expect(within(nav).getByRole('link', { name: 'Rückblick' })).toHaveAttribute('href', '#/auswertung/rueckblick')
    expect(within(nav).getByRole('link', { name: 'Rückblick' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Vorausschau' })).toHaveAttribute('href', '#/auswertung/vorausschau')
    expect(await screen.findByRole('table', { name: 'Rückblick' })).toBeInTheDocument()
  })

  // R14: default the 12 months before the current month
  it('looks back over the last 12 completed months by default', async () => {
    const fetchMock = renderPage()

    await screen.findByRole('table', { name: 'Rückblick' })
    expect(fetchMock).toHaveBeenCalledWith('/api/history?from=2025-11&to=2026-10', expect.anything())
    expect(screen.getByRole('button', { name: 'Letzte 12 Monate' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('November 2025 – Oktober 2026')).toBeInTheDocument()
    expect(tile('Ø Einnahmen')).toHaveTextContent('3.000,00 €')
    expect(tile('Ø Ausgaben')).toHaveTextContent('1.026,00 €')
    expect(tile('Ø Überschuss')).toHaveTextContent('1.974,00 €')
    // Decision 34: euros only
    expect(screen.queryByRole('button', { name: '%' })).not.toBeInTheDocument()
  })

  it('names how many months count when the first entry is later', async () => {
    renderPage()

    expect(await screen.findByText('Es zählen 9 Monate von 12, weil es erst ab Januar 2026 Einträge gibt.')).toBeInTheDocument()
  })

  it.each([
    ['Letzte 3 Monate', '/api/history?from=2026-08&to=2026-10'],
    ['Letzte 6 Monate', '/api/history?from=2026-05&to=2026-10'],
    ['Jahr 2025', '/api/history?from=2025-01&to=2025-12'],
    ['2026 bisher', '/api/history?from=2026-01&to=2026-10'],
  ])('loads the quick choice %s', async (label, url) => {
    const fetchMock = renderPage()
    await screen.findByRole('table', { name: 'Rückblick' })

    fireEvent.click(screen.getByRole('button', { name: label }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(url, expect.anything()))
    expect(screen.getByRole('button', { name: label })).toHaveAttribute('aria-pressed', 'true')
  })

  it('offers no "this year so far" in January', async () => {
    vi.setSystemTime(new Date(2027, 0, 10))
    renderPage()

    await screen.findByRole('table', { name: 'Rückblick' })
    expect(screen.getByRole('button', { name: 'Jahr 2026' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '2027 bisher' })).not.toBeInTheDocument()
  })

  it('looks back over a free period from month to month', async () => {
    const fetchMock = renderPage()
    await screen.findByRole('table', { name: 'Rückblick' })

    fireEvent.click(screen.getByRole('button', { name: 'Zeitraum …' }))
    expect(screen.getByLabelText('Von')).toHaveValue('2025-11')
    expect(screen.getByLabelText('Bis')).toHaveValue('2026-10')
    expect(screen.getByLabelText('Bis')).toHaveAttribute('max', '2026-10')
    fireEvent.change(screen.getByLabelText('Von'), { target: { value: '2024-01' } })
    fireEvent.change(screen.getByLabelText('Bis'), { target: { value: '2024-12' } })

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/history?from=2024-01&to=2024-12', expect.anything()))
  })

  it('shows the message of a rejected period', async () => {
    renderPage(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ status: 400, errors: [{ field: 'to', message: '„Bis“ darf nicht vor „Von“ liegen.' }] }),
          { status: 400 },
        ),
      ),
    )

    expect(await screen.findByText(/„Bis“ darf nicht vor „Von“ liegen\./)).toBeInTheDocument()
  })

  it('shows a chart and a table per month with Ø and total', async () => {
    renderPage()

    expect(await screen.findByRole('img', { name: 'Diagramm Einnahmen und Ausgaben je Monat' })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Rückblick' })
    const rows = within(table).getAllByRole('row')
    // header, 9 months, Ø, Gesamt
    expect(rows).toHaveLength(12)
    expect(within(table).getByRole('row', { name: /^Ø/ })).toHaveTextContent('3.000,001.026,001.974,00')
    expect(within(table).getByRole('row', { name: /Gesamt/ })).toHaveTextContent('27.000,009.234,0017.766,00')
  })

  // US13: a month opens with its income and expenses per category, with icon
  it('opens a month with its categories and icons', async () => {
    renderPage()
    const table = await screen.findByRole('table', { name: 'Rückblick' })

    const september = within(table).getByRole('button', { name: /September 2026/ })
    expect(september).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(september)

    expect(september).toHaveAttribute('aria-expanded', 'true')
    const wohnen = within(table).getByText('Wohnen').closest('tr') as HTMLElement
    expect(wohnen).toHaveTextContent('900,00')
    expect(wohnen.querySelector('.category-icon')).toHaveAttribute('data-icon', 'home')
    expect(within(table).getByText('Gehalt/Lohn (netto)').closest('tr')).toHaveTextContent('3.000,00')

    fireEvent.click(september)
    expect(within(table).queryByText('Wohnen')).not.toBeInTheDocument()
  })

  it('marks all rows of a month while the mouse is over one of them', async () => {
    renderPage()
    const table = await screen.findByRole('table', { name: 'Rückblick' })
    const row = within(table).getByRole('button', { name: /September 2026/ }).closest('tr') as HTMLElement

    fireEvent.mouseEnter(row)
    expect(row).toHaveClass('highlighted')
    fireEvent.mouseLeave(row)
    expect(row).not.toHaveClass('highlighted')
  })

  it('shows "–" and a hint without a counted month', async () => {
    renderPage(emptyHistory)

    expect(await screen.findByText(/noch keinen Monat mit Einträgen/)).toBeInTheDocument()
    expect(tile('Ø Ausgaben')).toHaveTextContent('–')
    expect(screen.queryByRole('table', { name: 'Rückblick' })).not.toBeInTheDocument()
  })

  // R14: the outlook starts at the current month and has no look back
  it('shows the reserve pot and the forecast on the Vorausschau', async () => {
    window.location.hash = '#/auswertung/vorausschau'
    const fetchMock = renderPage()

    expect(await screen.findByText('Ab November 2026, dem aktuellen Monat.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/reserves', expect.anything())
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringMatching(/history/), expect.anything())
    expect(screen.getByRole('link', { name: 'Vorausschau' })).toHaveAttribute('aria-current', 'page')
    expect(tile('Monatlich zurücklegen')).toHaveTextContent('20,00 €')
    expect(tile('Sollte jetzt angespart sein')).toHaveTextContent('20,00 €')
    expect(screen.getByText('Haftpflicht').closest('tr')).toHaveTextContent('15.10.2027')
    const forecast = screen.getByRole('table', { name: 'Vorschau 12 Monate' })
    expect(within(forecast).getAllByRole('row')).toHaveLength(13)
    expect(within(forecast).getByText('November 2026')).toBeInTheDocument()
    expect(within(forecast).getByText(/Strom: 200,00 €/)).toBeInTheDocument()
  })

  it('explains an outlook without rare expenses', async () => {
    window.location.hash = '#/auswertung/vorausschau'
    renderPage(history, emptyOutlook)

    expect(await screen.findByText(/Keine seltenen Ausgaben in den nächsten 12 Monaten/)).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /Verlauf des Topfs/ })).not.toBeInTheDocument()
  })

  // Decision 37: the course of the pot as a chart under the table of rare expenses
  it('shows the course of the pot as a chart', async () => {
    window.location.hash = '#/auswertung/vorausschau'
    renderPage()

    expect(await screen.findByRole('heading', { name: /Verlauf des Topfs/ })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Diagramm Verlauf des Topfs: Soll-Stand und Abbuchungen je Monat' }))
      .toBeInTheDocument()
  })

  // Decision 37: an info symbol explains the reserves, the tiles, two columns and the chart
  it('explains the reserves with info symbols', async () => {
    window.location.hash = '#/auswertung/vorausschau'
    renderPage()

    const general = await screen.findByRole('button', { name: 'Erklärung: Rücklagen' })
    fireEvent.click(general)
    expect(screen.getByRole('tooltip')).toHaveTextContent(/nur alle 3, 6 oder 12 Monate/)
    expect(screen.getByRole('tooltip')).toHaveTextContent(/was du tatsächlich zurückgelegt hast, kennt sie nicht/)
    fireEvent.keyDown(general, { key: 'Escape' })

    for (const label of ['Monatlich zurücklegen', 'Sollte jetzt angespart sein', 'Pro Monat', 'Angespart',
      'Verlauf des Topfs']) {
      expect(screen.getByRole('button', { name: `Erklärung: ${label}` })).toBeInTheDocument()
    }
    fireEvent.click(screen.getByRole('button', { name: 'Erklärung: Sollte jetzt angespart sein' }))
    expect(screen.getByRole('tooltip')).toHaveTextContent(/Sollwert, kein Kontostand/)
    expect(tile('Sollte jetzt angespart sein')).toHaveTextContent('20,00 €')
  })
})
