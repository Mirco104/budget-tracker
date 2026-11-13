import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Category, Plan, PlannedItem } from '../api/types'
import { currentMonth } from '../format'
import { PlanningPage } from './PlanningPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false },
  { id: 2, name: 'Mobilität', icon: 'car', archived: false, incomeOnly: false },
  { id: 3, name: 'Lebensmittel', icon: 'cart', archived: false, incomeOnly: false },
  { id: 4, name: 'Gehalt/Lohn (netto)', icon: 'wallet', archived: false, incomeOnly: true },
]

function item(overrides: Partial<PlannedItem>): PlannedItem {
  return {
    id: 1,
    itemKey: 'k1',
    name: 'Miete',
    amount: -900,
    saving: false,
    categoryId: 1,
    categoryName: 'Wohnen',
    accountId: 1,
    accountName: 'Girokonto',
    rhythmUnit: 'MONTH',
    rhythmCount: 1,
    anchorDate: '2026-01-01',
    entryMode: 'AUTO',
    workdaysOnly: false,
    validFrom: '2026-01',
    validTo: null,
    group: 'FIXED',
    monthlyShare: 900,
    ...overrides,
  }
}

const plan: Plan = {
  month: '2026-11',
  income: 2500,
  fixed: 1000,
  budgets: 400,
  saving: 300,
  unplanned: 800,
  savingRate: 12,
  planTotal: 1400,
  averageIncome: 2450,
  minimumIncome: 2300,
  averageMonths: 12,
  averageFrom: '2025-11',
  averageTo: '2026-10',
  incomeOverMinimum: 150,
  categories: [
    { group: 'INCOME', categoryId: 4, categoryName: 'Gehalt/Lohn (netto)', total: 2500 },
    { group: 'FIXED', categoryId: 2, categoryName: 'Mobilität', total: 100 },
    { group: 'FIXED', categoryId: 1, categoryName: 'Wohnen', total: 900 },
    { group: 'BUDGET', categoryId: 3, categoryName: 'Lebensmittel', total: 400 },
  ],
  items: [
    item({ id: 10, name: 'Gehalt', amount: 2500, categoryId: 4, group: 'INCOME', monthlyShare: 2500 }),
    item({ id: 1 }),
    item({
      id: 2, name: 'Kfz-Versicherung', amount: -1200, categoryId: 2, categoryName: 'Mobilität', rhythmCount: 12,
      anchorDate: '2026-03-15', monthlyShare: 100,
    }),
    item({
      id: 3, name: 'Lebensmittel', amount: -400, categoryId: 3, categoryName: 'Lebensmittel', rhythmUnit: null,
      rhythmCount: null, anchorDate: null, entryMode: 'MANUAL', group: 'BUDGET', monthlyShare: 400,
    }),
    item({
      id: 4, name: 'Notgroschen', amount: -300, saving: true, categoryId: null, categoryName: null, entryMode: 'TICK',
      group: 'SAVING', monthlyShare: 300,
    }),
  ],
}

function renderPage(data: Plan = plan) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    void init
    const body = url.startsWith('/api/plan')
      ? data
      : url.startsWith('/api/categories')
        ? categories
        : url.startsWith('/api/planned-items')
          ? item({ id: 99 })
          : []
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PlanningPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('PlanningPage', () => {
  it('shows the groups with their items, monthly shares and subtotals per category', async () => {
    renderPage()

    const fixed = await screen.findByRole('region', { name: 'Fixkosten' })
    expect(within(fixed).getByText('Wohnen')).toBeInTheDocument()
    // 27f2: categories open like the budget rows of the overview
    expect(within(fixed).queryByRole('button', { name: /Kfz-Versicherung/ })).not.toBeInTheDocument()
    fireEvent.click(within(fixed).getByRole('button', { name: /Mobilität/ }))
    fireEvent.click(within(fixed).getByRole('button', { name: /Wohnen/ }))
    expect(within(fixed).getByRole('button', { name: /Kfz-Versicherung/ })).toHaveTextContent('1.200,00 € jährlich')
    expect(within(fixed).getByRole('button', { name: /Kfz-Versicherung/ })).toHaveTextContent('100,00 €')
    expect(within(fixed).getByRole('button', { name: /Miete/ })).toHaveTextContent('automatisch')
    expect(screen.getByRole('region', { name: 'Budgets (variable Kosten)' })).toHaveTextContent('Lebensmittel')
    expect(screen.getByRole('region', { name: 'Sparen' })).toHaveTextContent('Notgroschen')
    // 27f4: average and weakest month as two tiles with their months
    const income = screen.getByRole('region', { name: 'Einkommen' })
    expect(income).toHaveTextContent('Durchschnitt2.450,00 €pro Monat')
    expect(income).toHaveTextContent('Mindestens2.300,00 €im schwächsten Monat')
    expect(income).toHaveTextContent('November 2025 – Oktober 2026 · 12 Monate')
    // 27f4: the tile Geplant warns only when the plan lies above the minimum income
    const planned = within(income).getByText('Geplant').closest('.income-stat') as HTMLElement
    expect(planned).toHaveClass('over')
    expect(planned).toHaveTextContent('150,00 € über Mindesteinnahmen')
    expect(screen.getByRole('region', { name: 'Einkommen' })).not.toHaveTextContent('Zur Orientierung')
  })

  // 27f4: the planned income stands above the bar
  it('shows the planned income above the bar', async () => {
    renderPage()
    const income = (await screen.findByText('Geplante Einnahmen')).closest('p') as HTMLElement
    expect(income).toHaveTextContent('2.500,00 €')
  })

  it('asks what to do with the part not planned', async () => {
    renderPage()

    expect(await screen.findByText('800,00 € sind noch nicht verplant.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Monatlich sparen' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Als Puffer in „Sonstiges“' })).toBeInTheDocument()
  })

  it('says when everything is planned and warns when overplanned', async () => {
    renderPage({ ...plan, unplanned: 0 })
    expect(await screen.findByText(/Alles verplant/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Monatlich sparen' })).not.toBeInTheDocument()
  })

  it('creates a budget for the rest under Sonstiges, creating the category first', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Als Puffer in „Sonstiges“' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/planned-items')).toBe(true))
    const category = fetchMock.mock.calls.find(([url, init]) => url === '/api/categories' && init?.method === 'POST')
    expect(JSON.parse(category![1]!.body as string)).toEqual({ name: 'Sonstiges' })
    const created = fetchMock.mock.calls.find(([url]) => url === '/api/planned-items')
    expect(JSON.parse(created![1]!.body as string)).toMatchObject({ name: 'Sonstiges', amount: -800, rhythmUnit: null })
  })

  // 27f4: the item dialog uses the shared selection with "+ Neue Kategorie …"
  it('offers a new category right in the item dialog', async () => {
    renderPage()
    const fixed = await screen.findByRole('region', { name: 'Fixkosten' })
    fireEvent.click(within(fixed).getByRole('button', { name: '+ Posten' }))
    const dialog = await screen.findByRole('dialog', { name: 'Neuer Posten' })
    fireEvent.change(within(dialog).getByLabelText('Kategorie'), { target: { value: 'new' } })
    expect(within(dialog).getByRole('textbox', { name: 'Neue Kategorie' })).toBeInTheDocument()
  })

  it('creates a yearly item with its due month and the month "Gilt ab"', async () => {
    const fetchMock = renderPage()
    const fixed = await screen.findByRole('region', { name: 'Fixkosten' })
    fireEvent.click(within(fixed).getByRole('button', { name: '+ Posten' }))

    const dialog = await screen.findByRole('dialog', { name: 'Neuer Posten' })
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Rundfunkbeitrag' } })
    fireEvent.change(within(dialog).getByLabelText('Wie oft'), { target: { value: 'MONTH:12' } })
    fireEvent.change(within(dialog).getByLabelText('Betrag pro Zahlung'), { target: { value: '220,32' } })
    fireEvent.change(within(dialog).getByLabelText('Fällig im'), { target: { value: '3' } })
    fireEvent.change(within(dialog).getByLabelText('Am Tag'), { target: { value: '15' } })
    fireEvent.change(within(dialog).getByLabelText('Kategorie'), { target: { value: '1' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/planned-items')).toBe(true))
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/planned-items')
    const month = currentMonth()
    expect(JSON.parse(call![1]!.body as string)).toEqual({
      name: 'Rundfunkbeitrag',
      amount: -220.32,
      saving: false,
      categoryId: 1,
      rhythmUnit: 'MONTH',
      rhythmCount: 12,
      anchorDate: `${month.slice(0, 4)}-03-15`,
      entryMode: 'AUTO',
      workdaysOnly: false,
      validFrom: month,
      savingGoalId: null,
    })
  })

  it('changes an item from the month the page shows', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Wohnen/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Miete/ }))

    const dialog = await screen.findByRole('dialog', { name: 'Posten bearbeiten' })
    fireEvent.change(within(dialog).getByLabelText('Betrag pro Zahlung'), { target: { value: '950' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(true))
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(call![0]).toBe('/api/planned-items/1')
    expect(JSON.parse(call![1]!.body as string)).toMatchObject({ amount: -950, validFrom: currentMonth() })
  })

  // 27f3: "Monatlich sparen" splits the rest onto the saving items that exist
  it('splits the part not planned onto the saving items', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Monatlich sparen' }))

    const dialog = await screen.findByRole('dialog', { name: /800,00.€ aufteilen/ })
    expect(within(dialog).getByRole('status')).toHaveTextContent('noch 800,00 € offen')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Rest' }))
    expect(within(dialog).getByLabelText('Mehr für Notgroschen')).toHaveValue('800,00')
    expect(within(dialog).getByRole('status')).toHaveTextContent('noch 0,00 € offen')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/planned-items/4' && init?.method === 'PUT')).toBe(true))
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/planned-items/4' && init?.method === 'PUT')
    expect(JSON.parse(call![1]!.body as string)).toMatchObject({ amount: -1100, saving: true, validFrom: currentMonth() })
  })

  // 27f3: the budget of "Sonstiges" is named in the head
  it('names the budget of Sonstiges in the head', async () => {
    renderPage({
      ...plan,
      categories: [...plan.categories, { group: 'BUDGET', categoryId: 9, categoryName: 'Sonstiges', total: 120 }],
    })
    expect(await screen.findByText('davon Sonstiges 120,00 €')).toBeInTheDocument()
  })
})
