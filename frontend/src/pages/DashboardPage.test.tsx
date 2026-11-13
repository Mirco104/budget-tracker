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
  minimumIncome: 2000,
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
  unplannedMinimum: 1040,
  unplannedMinimumLabel: 'Nicht verplant',
  surplus: 1105,
  surplusLabel: 'Monatlicher Überschuss',
  categories: [
    { id: 1, name: 'Wohnen', target: 800, actual: 780, difference: 20 },
    { id: 3, name: 'Versicherungen', target: 60, actual: 80, difference: -20 },
    { id: 8, name: 'Freizeit', target: 100, actual: 0, difference: 100 },
    { id: 9, name: 'Urlaub', target: null, actual: 35, difference: null },
  ],
  income: [{ id: 20, name: 'Gehalt', actual: 2000 }],
  saving: { planned: 300, rate: 12, items: [{ name: 'Notgroschen', monthly: 300 }] },
  expected: { count: 3, total: -170 },
}

// The plan of a category comes with the dashboard (P3); the number stays for the readability of the fixtures
function category(id: number, name: string, icon: string, plan: number | null = null): Category {
  void plan
  return {
    id,
    name,
    icon,
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
    id: 4,
    amount: -780,
    description: 'Miete',
    date: '2026-10-01',
    categoryId: 1,
    categoryName: 'Wohnen',
    accountId: 1,
    accountName: 'Girokonto',
    transfer: false,
    auto: true,
    fix: true,
  },
  {
    id: 7,
    amount: -35,
    description: 'Fähre',
    date: '2026-10-12',
    categoryId: 9,
    categoryName: 'Urlaub',
    accountId: 1,
    accountName: 'Girokonto',
    transfer: false,
    auto: false, fix: false,
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
  it('requests the selected month and shows income, expenses, saving and expected payments', async () => {
    const fetchMock = renderPage()

    expect(await screen.findByText('Sparen', { selector: 'dt' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-10', expect.anything())
    expect(tile('Einnahmen')).toHaveTextContent('2.000,00 €')
    expect(screen.getByText(/^Ø 2\.000,00 €/)).toHaveTextContent('Oktober 2025 – September 2026, 12 Monate')
    expect(tile('Ausgaben')).toHaveTextContent('895,00 €')
    expect(screen.getByText(/^fix /)).toHaveTextContent('fix 840,00 € · variabel 55,00 €')
    // R23, decision 34: Ā over the same months as the Ø income
    expect(screen.getByText(/^Ø 912,50 €/)).toHaveTextContent('Oktober 2025 – September 2026, 12 Monate')
    // AP 27d: Sparen and Erwartet instead of the surplus tile
    expect(tile('Sparen')).toHaveTextContent('300,00 €')
    expect(screen.getByText(/Sparquote 12 %/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '3 Zahlungen im Oktober' })).toHaveAttribute('href', '#/transaktionen/erwartet')
    expect(screen.queryByText('Monatlicher Überschuss')).not.toBeInTheDocument()
    expect(screen.getByText('Abgeschlossener Monat.')).toBeInTheDocument()
    expect(screen.queryByText(/Nicht verplant/)).not.toBeInTheDocument()
  })

  // US27: booking also starts on the overview, with the last used categories of the month
  it('opens the booking dialog with "+ Buchung" and the round button', async () => {
    renderPage()
    await screen.findByText('Sparen', { selector: 'dt' })

    fireEvent.click(screen.getByRole('button', { name: 'Buchung' }))

    const dialog = screen.getByRole('dialog', { name: 'Neue Buchung' })
    // A past month starts at its first day
    expect(within(dialog).getByLabelText('Datum')).toHaveValue('2026-10-01')
    await waitFor(() =>
      expect(within(dialog).getByRole('group', { name: 'Zuletzt genutzt' })).toHaveTextContent('UrlaubWohnen'),
    )
    fireEvent.click(within(dialog).getByRole('button', { name: 'Schließen' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Neue Buchung' }))
    expect(screen.getByRole('dialog', { name: 'Neue Buchung' })).toBeInTheDocument()
  })

  // Decision 34: euros only
  it('has no switch between euros and percent', async () => {
    renderPage()

    expect(await screen.findByText('Sparen', { selector: 'dt' })).toBeInTheDocument()
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

    expect(await screen.findByText('Sparen', { selector: 'dt' })).toBeInTheDocument()
    expect(screen.queryByText(/^Ø /)).not.toBeInTheDocument()
  })

  it('shows a month without income', async () => {
    renderPage({ ...dashboard, incomeTotal: 0, surplus: -895, surplusLabel: 'Monatlicher Verlust', income: [] })

    expect(await screen.findByText('Einnahmen', { selector: 'dt' })).toBeInTheDocument()
    expect(tile('Einnahmen')).toHaveTextContent('0,00 €')
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
    expect(bookings.getByText('fix')).toBeInTheDocument()
    expect(bookings.getByText('01.10.2026')).toBeInTheDocument()
    expect(bookings.queryByText('Fähre')).not.toBeInTheDocument()

    fireEvent.click(wohnen)
    expect(screen.queryByText('Miete')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Freizeit/ }))
    expect(await screen.findByText('Keine Buchungen in diesem Monat.')).toBeInTheDocument()
  })

  it('shows no first steps in an empty past month', async () => {
    renderPage(
      { ...dashboard, categories: [], income: [] },
      categories.map((c) => ({ ...c, targetValidFrom: null })),
    )

    expect(await screen.findByText('In diesem Monat gibt es keine Einnahmen.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Erste Schritte' })).not.toBeInTheDocument()
  })

  // P8, AP 27e: a green line under the budget row for a rare item, orange when the pot was too small
  it('shows what a rare item put aside and what its pot paid', async () => {
    const pot = {
      itemId: 5, name: 'Kfz-Versicherung', categoryId: 1, amount: 1200, monthly: 100, balance: 1000, due: false,
      nextDue: '2027-03-15', paid: 0, fromPot: 0, shortfall: 0,
    }
    renderPage({ ...dashboard, reserves: [pot] })
    expect(
      await screen.findByText('100,00 € zurückgelegt für Kfz-Versicherung · Rücklage 1.000,00 € von 1.200,00 €'),
    ).toBeInTheDocument()
  })

  it('shows in orange what was missing in the pot', async () => {
    const pot = {
      itemId: 5, name: 'Kfz-Versicherung', categoryId: 1, amount: 1200, monthly: 100, balance: 1000, due: true,
      nextDue: '2026-10-15', paid: 1200, fromPot: 1000, shortfall: 200,
    }
    renderPage({ ...dashboard, reserves: [pot] })
    const line = await screen.findByText('1.000,00 € aus der Rücklage, 200,00 € fehlten darin')
    expect(line).toHaveClass('short')
  })
})
