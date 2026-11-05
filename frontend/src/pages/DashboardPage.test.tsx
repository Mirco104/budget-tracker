import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category, DashboardData, Transaction } from '../api/types'
import { DashboardPage } from './DashboardPage'

// Fictional values, shaped like the backend response.
const dashboard: DashboardData = {
  month: '2026-10',
  monthState: 'PAST',
  incomeTotal: 2000,
  averageIncome: 2000,
  averageExpenses: 912.5,
  averageIncomeMonths: 12,
  averageIncomeFrom: '2025-10',
  averageIncomeTo: '2026-09',
  expenses: {
    target: 960,
    actual: 895,
    difference: 65,
    recurring: 840,
    oneOff: 55,
  },
  unplanned: 1040,
  unplannedLabel: 'Nicht verplant',
  surplus: 1105,
  surplusLabel: 'Monatlicher Überschuss',
  categories: [
    { id: 1, name: 'Wohnen', target: 800, actual: 780, difference: 20 },
    { id: 3, name: 'Versicherungen', target: 60, actual: 80, difference: -20 },
    { id: 8, name: 'Freizeit', target: 100, actual: 0, difference: 100 },
    { id: 9, name: 'Urlaub', target: null, actual: 35, difference: null },
  ],
  income: [{ id: 20, name: 'Gehalt', actual: 2000 }],
}

function category(id: number, name: string, icon: string, targetAmount: number | null = null): Category {
  return {
    id,
    name,
    icon,
    sortOrder: id * 10,
    targetAmount,
    targetPercent: null,
    targetValidFrom: targetAmount === null ? null : '2026-01',
    archived: false,
    incomeOnly: false,
  }
}

const categories: Category[] = [
  category(1, 'Wohnen', 'home', 800),
  category(3, 'Versicherungen', 'shield', 60),
  category(8, 'Freizeit', 'music', 100),
  category(9, 'Urlaub', 'suitcase'),
  category(20, 'Gehalt', 'wallet'),
]

const transactions: Transaction[] = [
  {
    kind: 'RECURRING',
    id: null,
    recurringId: 4,
    occurrenceDate: '2026-10-01',
    amount: -780,
    description: 'Miete',
    date: '2026-10-01',
    categoryId: 1,
    categoryName: 'Wohnen',
  },
  {
    kind: 'SINGLE',
    id: 7,
    recurringId: null,
    occurrenceDate: null,
    amount: -35,
    description: 'Fähre',
    date: '2026-10-12',
    categoryId: 9,
    categoryName: 'Urlaub',
  },
]

function json(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
}

function renderPage(data: DashboardData = dashboard, categoryList: Category[] = categories) {
  const fetchMock = vi.fn((url: string) => {
    if (url.startsWith('/api/dashboard')) return json(data)
    if (url.startsWith('/api/categories')) return json(categoryList)
    if (url.startsWith('/api/transactions')) return json(transactions)
    return new Promise<Response>(() => {})
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DashboardPage month={data.month} />
    </QueryClientProvider>,
  )
  return fetchMock
}

function tile(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextSibling as HTMLElement
}

function item(name: string) {
  return screen.getByText(name, { selector: '.budget-name' }).closest('li') as HTMLElement
}

describe('DashboardPage', () => {
  it('requests the selected month and shows income and expenses with their averages and the surplus', async () => {
    const fetchMock = renderPage()

    expect(await screen.findByText('Monatlicher Überschuss')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-10', expect.anything())
    expect(tile('Einnahmen')).toHaveTextContent('2.000,00 €')
    expect(screen.getByText(/^Ø 2\.000,00 €/)).toHaveTextContent('Oktober 2025 – September 2026, 12 Monate')
    expect(tile('Ausgaben')).toHaveTextContent('895,00 €')
    expect(screen.getByText(/davon wiederkehrend/)).toHaveTextContent('840,00 €')
    expect(screen.getByText(/davon einmalig/)).toHaveTextContent('55,00 €')
    // R23, decision 34: Ā over the same months as the Ø income
    expect(screen.getByText(/^Ø 912,50 €/)).toHaveTextContent('Oktober 2025 – September 2026, 12 Monate')
    expect(tile('Monatlicher Überschuss')).toHaveTextContent('1.105,00 €')
    expect(screen.getByText('Abgeschlossener Monat.')).toBeInTheDocument()
    expect(screen.queryByText(/Nicht verplant/)).not.toBeInTheDocument()
  })

  // Decision 34: euros only
  it('has no switch between euros and percent', async () => {
    renderPage()

    expect(await screen.findByText('Monatlicher Überschuss')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '%' })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Anzeige' })).not.toBeInTheDocument()
  })

  it('leaves out both averages when no month counts', async () => {
    renderPage({
      ...dashboard,
      averageIncome: null,
      averageExpenses: null,
      averageIncomeMonths: 0,
      averageIncomeFrom: null,
      averageIncomeTo: null,
    })

    expect(await screen.findByText('Monatlicher Überschuss')).toBeInTheDocument()
    expect(screen.queryByText(/^Ø /)).not.toBeInTheDocument()
  })

  it('calls the surplus "Noch frei" in the current month', async () => {
    renderPage({ ...dashboard, monthState: 'CURRENT', surplusLabel: 'Noch frei' })

    expect(await screen.findByText('Noch frei', { selector: 'dt' })).toBeInTheDocument()
    expect(screen.getByText(/Laufender Monat/)).toBeInTheDocument()
  })

  // R7, decision 34: the amount keeps its minus sign, in the warning color
  it('shows an overrun with minus sign in the warning color', async () => {
    renderPage({ ...dashboard, monthState: 'CURRENT', surplus: -120.5, surplusLabel: 'Überschritten' })

    expect(await screen.findByText('Überschritten', { selector: 'dt' })).toBeInTheDocument()
    expect(tile('Überschritten')).toHaveTextContent('-120,50 €')
    expect(tile('Überschritten')).toHaveClass('negative')
  })

  it('shows each category with icon, actual of target, progress and what is left', async () => {
    renderPage()

    const wohnen = await screen.findByRole('button', { name: /Wohnen/ })
    expect(wohnen).toHaveTextContent('780,00 € von 800,00 €')
    expect(wohnen).toHaveTextContent('noch 20,00 €')
    await waitFor(() => expect(wohnen.querySelector('.category-icon')).toHaveAttribute('data-icon', 'home'))
    expect(within(item('Wohnen')).getByTestId('progress')).not.toHaveClass('over')

    const versicherungen = item('Versicherungen')
    expect(within(versicherungen).getByText('20,00 € über dem Soll')).toHaveClass('over')
    expect(within(versicherungen).getByTestId('progress')).toHaveClass('over')

    const urlaub = item('Urlaub')
    expect(urlaub).toHaveTextContent('35,00 € · kein Soll')
    expect(within(urlaub).queryByTestId('progress')).not.toBeInTheDocument()

    const total = item('Gesamt')
    expect(total).toHaveTextContent('895,00 € von 960,00 €')
    expect(total).toHaveTextContent('noch 65,00 €')

    expect(item('Gehalt')).toHaveTextContent('2.000,00 €')
  })

  it('shows a month without income', async () => {
    renderPage({ ...dashboard, incomeTotal: 0, surplus: -895, surplusLabel: 'Monatlicher Verlust', income: [] })

    expect(await screen.findByText('Monatlicher Verlust', { selector: 'dt' })).toBeInTheDocument()
    expect(tile('Einnahmen')).toHaveTextContent('0,00 €')
    expect(tile('Monatlicher Verlust')).toHaveTextContent('-895,00 €')
    expect(item('Wohnen')).toHaveTextContent('780,00 € von 800,00 €')
    expect(screen.getByText('In diesem Monat gibt es keine Einnahmen.')).toBeInTheDocument()
  })

  // US12
  it('expands a category with its bookings and occurrences in the month', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('button', { name: /Wohnen/ })
    expect(wohnen).toHaveAttribute('aria-expanded', 'false')
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringMatching(/^\/api\/transactions/), expect.anything())

    fireEvent.click(wohnen)

    expect(await screen.findByText('Miete')).toBeInTheDocument()
    expect(wohnen).toHaveAttribute('aria-expanded', 'true')
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions?month=2026-10', expect.anything())
    const bookings = within(item('Wohnen'))
    expect(bookings.getByText('wiederkehrend')).toBeInTheDocument()
    expect(bookings.getByText('01.10.2026')).toBeInTheDocument()
    expect(bookings.queryByText('Fähre')).not.toBeInTheDocument()

    fireEvent.click(wohnen)
    expect(screen.queryByText('Miete')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Freizeit/ }))
    expect(await screen.findByText('Keine Buchungen in diesem Monat.')).toBeInTheDocument()
  })

  it('says so when there is nothing to show', async () => {
    renderPage({ ...dashboard, categories: [] })

    expect(await screen.findByText('In diesem Monat gibt es weder Soll-Werte noch Ausgaben.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Erste Schritte' })).not.toBeInTheDocument()
  })

  it('shows the first steps in an empty current month without targets', async () => {
    const empty: DashboardData = {
      ...dashboard,
      month: '2026-11',
      monthState: 'CURRENT',
      surplusLabel: 'Noch frei',
      categories: [],
      income: [],
    }
    renderPage(empty, categories.map((c) => ({ ...c, targetAmount: null, targetValidFrom: null })))

    const steps = await screen.findByRole('region', { name: 'Erste Schritte' })
    expect(within(steps).getByRole('link', { name: 'Soll festlegen' })).toHaveAttribute('href', '#/planung')
    expect(within(steps).getByRole('link', { name: 'Erste Buchung erfassen' })).toHaveAttribute(
      'href',
      '#/transaktionen',
    )
  })

  it('shows no first steps in an empty past month', async () => {
    renderPage(
      { ...dashboard, categories: [], income: [] },
      categories.map((c) => ({ ...c, targetAmount: null, targetValidFrom: null })),
    )

    expect(await screen.findByText('In diesem Monat gibt es keine Einnahmen.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Erste Schritte' })).not.toBeInTheDocument()
  })
})
