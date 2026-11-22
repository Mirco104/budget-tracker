import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BudgetSuggestion, Category, Me, Plan, PlannedItem } from '../api/types'
import { SetupPage } from './SetupPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false, parentId: null, path: 'Wohnen' },
  { id: 3, name: 'Lebensmittel', icon: 'cart', archived: false, incomeOnly: false, parentId: null, path: 'Lebensmittel' },
  { id: 4, name: 'Gehalt/Lohn (netto)', icon: 'wallet', archived: false, incomeOnly: true, parentId: null, path: 'Gehalt/Lohn (netto)' },
]

const newUser: Me = { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'USER', istStage: 2, setupDone: false }

const emptyPlan: Plan = {
  month: '2026-11', income: 0, fixed: 0, budgets: 0, saving: 0, unplanned: 0, savingRate: null, planTotal: 0,
  averageIncome: null, minimumIncome: null, averageMonths: 0, categories: [], items: [],
}

function item(overrides: Partial<PlannedItem>): PlannedItem {
  return {
    id: 1, itemKey: 'k1', name: 'Gehalt', amount: 2500, saving: false, categoryId: 4, categoryName: 'Gehalt/Lohn (netto)',
    accountId: 1, accountName: 'Girokonto', rhythmUnit: 'MONTH', rhythmCount: 1, anchorDate: '2026-01-28', entryMode: 'AUTO',
    workdaysOnly: false, validFrom: '2026-01', validTo: null, group: 'INCOME', monthlyShare: 2500, planCategoryId: 4,
    ...overrides,
  }
}

const fullPlan: Plan = {
  ...emptyPlan,
  income: 2500,
  fixed: 900,
  budgets: 400,
  saving: 200,
  unplanned: 1000,
  savingRate: 8,
  categories: [
    { group: 'INCOME', categoryId: 4, categoryName: 'Gehalt/Lohn (netto)', total: 2500 },
    { group: 'FIXED', categoryId: 1, categoryName: 'Wohnen', total: 900 },
    { group: 'BUDGET', categoryId: 3, categoryName: 'Lebensmittel', total: 400 },
  ],
  items: [
    item({ id: 10 }),
    item({ id: 11, name: 'Miete', amount: -900, categoryId: 1, categoryName: 'Wohnen', group: 'FIXED', monthlyShare: 900, planCategoryId: 1 }),
    item({
      id: 12, name: 'Lebensmittel', amount: -400, categoryId: 3, categoryName: 'Lebensmittel', rhythmUnit: null, rhythmCount: null,
      anchorDate: null, entryMode: 'MANUAL', group: 'BUDGET', monthlyShare: 400, planCategoryId: 3,
    }),
    item({
      id: 13, name: 'Notgroschen', amount: -200, saving: true, categoryId: null, categoryName: null, group: 'SAVING',
      monthlyShare: 200, planCategoryId: null,
    }),
  ],
}

const girokonto = {
  id: 1, name: 'Girokonto', iban: null, role: 'EVERYDAY', defaultAccount: true, archived: false, balance: 1500,
  statementDate: '2026-11-21', statementAmount: 1500, lowerLimitMode: 'OFF', lowerLimit: null, belowLimit: false,
}

const budgetSuggestions: BudgetSuggestion[] = [
  { name: 'Shopping', categoryId: null, bankCategory: 'Shopping', average: 138.42, proposal: 138, months: 6 },
]

function renderSetup(hash: string, { me = newUser, plan = emptyPlan, suggestions = [] as BudgetSuggestion[] } = {}) {
  window.location.hash = hash
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    let body: unknown = []
    if (url === '/api/account/stage' || url === '/api/account/setup') {
      body = { ...me, ...JSON.parse(init?.body as string) }
    } else if (url === '/api/plan/budget-suggestions') {
      body = suggestions
    } else if (url === '/api/plan/suggestions') {
      body = []
    } else if (url.startsWith('/api/plan')) {
      body = plan
    } else if (url.startsWith('/api/categories')) {
      body = categories
    } else if (url === '/api/accounts') {
      body = [{ role: 'EVERYDAY', total: 1500, accounts: [girokonto] }]
    }
    void method
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SetupPage me={me} />
    </QueryClientProvider>,
  )
  return fetchMock
}

function calls(fetchMock: ReturnType<typeof renderSetup>, url: string) {
  return fetchMock.mock.calls.filter(([u, init]) => u === url && init?.method === 'PUT').map(([, init]) => JSON.parse(init?.body as string))
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.location.hash = ''
  window.sessionStorage.clear()
})

describe('SetupPage', () => {
  it('lets a new user go on only after choosing how to start', async () => {
    renderSetup('#/einrichtung')

    expect(await screen.findByRole('heading', { name: 'Willkommen! Lass uns deinen Monat planen.' })).toBeInTheDocument()
    const next = screen.getByRole('button', { name: 'Weiter' })
    expect(next).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: /Plan aus Kontoauszügen vorschlagen/ }))
    expect(screen.getByRole('link', { name: 'Kontoauszug importieren' })).toHaveAttribute('href', '#/transaktionen/import')
    expect(next).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: /Plan selbst eintragen/ }))
    expect(next).toBeEnabled()
    fireEvent.click(next)
    expect(window.location.hash).toBe('#/einrichtung/1')
  })

  it('greets an existing user with the data already there', async () => {
    renderSetup('#/einrichtung', { me: { ...newUser, istStage: 4 }, plan: fullPlan })

    expect(await screen.findByRole('heading', { name: 'Willkommen zurück! Lass uns deinen Plan prüfen.' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeEnabled()
  })

  it('stores the chosen stage at once', async () => {
    const fetchMock = renderSetup('#/einrichtung/1')

    fireEvent.click(await screen.findByRole('radio', { name: 'Stufe 3: Buchungen' }))
    await waitFor(() => expect(calls(fetchMock, '/api/account/stage')).toEqual([{ stage: 3 }]))
    expect(screen.getByRole('radio', { name: 'Stufe 2: Kontostand' })).toHaveTextContent('empfohlen')
  })

  it('needs an income above 0 € and offers typical items that open the item window, entered automatically', async () => {
    renderSetup('#/einrichtung/3')

    expect(await screen.findByRole('heading', { name: 'Was kommt rein?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeDisabled()
    expect(screen.getByText('Trag mindestens ein Einkommen über 0 € ein, um weiterzumachen.')).toBeInTheDocument()

    const typical = screen.getByRole('region', { name: 'Häufig dazu' })
    const gehalt = within(typical).getByText('Gehalt').closest('li') as HTMLElement
    fireEvent.click(within(gehalt).getByRole('button', { name: 'Hinzufügen' }))

    const dialog = await screen.findByRole('dialog', { name: 'Neuer Posten' })
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Gehalt')
    expect(within(dialog).getByLabelText('Eintragen')).toHaveValue('AUTO')
  })

  it('shows the plan so far with later steps pending and the budget suggestions from the bookings', async () => {
    renderSetup('#/einrichtung/5', { plan: fullPlan, suggestions: budgetSuggestions })

    const sum = await screen.findByLabelText('Dein Plan bisher')
    expect(sum).toHaveTextContent('Bleibt fürs Sparen1.200,00 €')
    expect(sum).toHaveTextContent('Sparenausstehend')
    const suggestions = await screen.findByRole('region', { name: 'Vorschläge aus deinen Buchungen' })
    expect(suggestions).toHaveTextContent('Shopping')
    expect(suggestions).toHaveTextContent('Kategorie der Bank · Ø 138,42 € im Monat · 6 Monate')
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeEnabled()
  })

  it('finishes on the last page and can be put off on the first', async () => {
    const fetchMock = renderSetup('#/einrichtung/8', { plan: fullPlan })

    expect(await screen.findByRole('heading', { name: 'Fertig! So läuft dein Monat.' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Fixkosten' })).toHaveTextContent('Miete')
    expect(screen.getByRole('link', { name: 'Fixkosten ändern' })).toHaveAttribute('href', '#/einrichtung/4')
    fireEvent.click(screen.getByRole('button', { name: 'Einrichtung abschließen' }))
    await waitFor(() => expect(calls(fetchMock, '/api/account/setup')).toEqual([{ done: true }]))
    await waitFor(() => expect(window.location.hash).toBe('#/uebersicht'))
  })

  it('puts the setup off without changes', async () => {
    const fetchMock = renderSetup('#/einrichtung')

    fireEvent.click(await screen.findByRole('button', { name: 'Später einrichten' }))
    await waitFor(() => expect(calls(fetchMock, '/api/account/setup')).toEqual([{ done: true }]))
  })
})
