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
    if (url === '/api/auth/me') {
      body = me
    } else if (url === '/api/account/stage' || url === '/api/account/setup') {
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

  // R35, decision 86: the stages show only after both questions; the smaller stage of wish and time is recommended
  it('recommends a stage from the two questions and stores the chosen one', async () => {
    const fetchMock = renderSetup('#/einrichtung/1')

    expect(await screen.findByRole('heading', { name: 'Wie genau willst du sehen, was du ausgibst?' })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: 'Stufe 3: Buchungen' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeDisabled()

    fireEvent.click(screen.getByRole('radio', { name: /… und wofür genau/ }))
    fireEvent.click(screen.getByRole('radio', { name: 'Ein paar Minuten pro Woche' }))

    expect(screen.getByText(/bräuchtest du mindestens „Was nötig ist“/)).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Stufe 3: Buchungen' })).toHaveTextContent('Für dich empfohlen')
    await waitFor(() => expect(calls(fetchMock, '/api/account/stage')).toEqual([{ stage: 3 }]))
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeEnabled()

    fireEvent.click(screen.getByRole('radio', { name: 'Stufe 4: Buchungen mit Kategorien' }))
    await waitFor(() => expect(calls(fetchMock, '/api/account/stage')).toEqual([{ stage: 3 }, { stage: 4 }]))
  })

  // Decision 90: with items a switch to stage 1 asks first and is not chosen by the recommendation alone
  it('asks before an existing user switches to stage 1', async () => {
    const fetchMock = renderSetup('#/einrichtung/1', { me: { ...newUser, istStage: 4 }, plan: fullPlan })

    fireEvent.click(await screen.findByRole('radio', { name: /Wie ich mein Einkommen aufteile/ }))
    fireEvent.click(screen.getByRole('radio', { name: 'Gar keine' }))
    expect(screen.getByRole('radio', { name: 'Stufe 1: Nur Plan' })).toHaveTextContent('Für dich empfohlen')
    expect(calls(fetchMock, '/api/account/stage')).toEqual([])

    fireEvent.click(screen.getByRole('radio', { name: 'Stufe 1: Nur Plan' }))
    const dialog = await screen.findByRole('dialog', { name: 'Auf Stufe 1: Nur Plan wechseln' })
    expect(dialog).toHaveTextContent('In Stufe 1 trägt die App nichts mehr ein.')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Stufe wechseln' }))
    await waitFor(() => expect(calls(fetchMock, '/api/account/stage')).toEqual([{ stage: 1 }]))
  })

  it('needs an income above 0 € and offers typical items as tiles that open the item window, entered automatically', async () => {
    renderSetup('#/einrichtung/3')

    expect(await screen.findByRole('heading', { name: 'Was kommt jeden Monat rein?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeDisabled()
    expect(screen.getByText('Trag mindestens ein Einkommen über 0 € ein, um weiterzumachen.')).toBeInTheDocument()

    const typical = screen.getByRole('region', { name: 'Häufig dazu' })
    fireEvent.click(within(typical).getByRole('button', { name: 'Gehalt hinzufügen' }))

    const dialog = await screen.findByRole('dialog', { name: 'Neuer Posten' })
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Gehalt')
    expect(within(dialog).getByLabelText('Eintragen')).toHaveValue('AUTO')
    // Decision 89: stage 2 enters an item with a term automatically or on ticking only
    await waitFor(() =>
      expect(within(within(dialog).getByLabelText('Eintragen')).getAllByRole('option').map((o) => o.textContent)).toEqual([
        'beim Abhaken',
        'automatisch am Termin',
      ]),
    )
  })

  // Decision 89: in stage 1 the item window has no field "Eintragen" and saves "selbst anlegen"
  it('creates items without automatic entries in stage 1', async () => {
    const fetchMock = renderSetup('#/einrichtung/3', { me: { ...newUser, istStage: 1 } })

    fireEvent.click(await screen.findByRole('button', { name: 'Gehalt hinzufügen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Neuer Posten' })
    await waitFor(() => expect(within(dialog).queryByLabelText('Eintragen')).not.toBeInTheDocument())
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(([u, init]) => u === '/api/planned-items' && init?.method === 'POST')
      expect(JSON.parse(post?.[1]?.body as string)).toMatchObject({ name: 'Gehalt', entryMode: 'MANUAL' })
    })
  })

  // Decision 88: the income as a bar that fills step by step; later steps "kommt noch", the rest "Noch frei"
  it('shows the plan so far as a bar with later steps to come and the budget suggestions from the bookings', async () => {
    renderSetup('#/einrichtung/5', { plan: fullPlan, suggestions: budgetSuggestions })

    const bar = await screen.findByLabelText('Dein Einkommen im Plan')
    expect(bar).toHaveTextContent('Fixkosten 900,00 €')
    expect(bar).toHaveTextContent('Variable Kosten 400,00 €')
    expect(bar).toHaveTextContent('Sparen kommt noch')
    expect(bar).toHaveTextContent('Noch frei 1.200,00 €')
    expect(bar).toHaveTextContent('Ganzer Balken = Einkommen 2.500,00 €')
    expect(screen.getByRole('link', { name: /Fixkosten/ })).toHaveAttribute('href', '#/einrichtung/4')
    const suggestions = await screen.findByRole('region', { name: 'Vorschläge aus deinen Buchungen' })
    expect(suggestions).toHaveTextContent('Shopping')
    expect(suggestions).toHaveTextContent('Kategorie der Bank · Ø 138,42 € im Monat · 6 Monate')
    expect(screen.getByRole('button', { name: 'Weiter' })).toBeEnabled()
  })

  it('finishes on the last page and can be put off on the first', async () => {
    const fetchMock = renderSetup('#/einrichtung/8', { plan: fullPlan })

    expect(await screen.findByRole('heading', { name: 'Fertig! So läuft dein Monat.' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'So läuft dein Monat' })).toHaveTextContent('Kontostand eintragen, die Glocke erinnert dich.')
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
