import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { PlanningPage } from './PlanningPage'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', sortOrder: 1, targetAmount: 800, targetPercent: null, targetValidFrom: '2026-09', archived: false, incomeOnly: false },
  { id: 7, name: 'Lebensmittel', icon: 'cart', sortOrder: 7, targetAmount: null, targetPercent: 0.15, targetValidFrom: '2026-11', archived: false, incomeOnly: false },
  { id: 8, name: 'Freizeit', icon: 'tag', sortOrder: 8, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  // R24: only positive entries and no target, hidden by default
  { id: 20, name: 'Gehalt/Lohn (netto)', icon: 'wallet', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: true },
  // R24: income only, but with a target in the month, so it stays
  { id: 21, name: 'Nebenjob', icon: 'tag', sortOrder: 21, targetAmount: 10, targetPercent: null, targetValidFrom: '2026-11', archived: false, incomeOnly: true },
  // US10: archived categories are not planned
  { id: 30, name: 'Kredite', icon: 'tag', sortOrder: 30, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: true, incomeOnly: false },
]

// Only the fields the planning page uses.
const average = { averageIncome: 3200, averageIncomeMonths: 9, averageIncomeFrom: '2026-02', averageIncomeTo: '2026-10' }
const dashboard = {
  incomeTotal: 1000,
  ...average,
  expenses: { target: 3000 },
  unplanned: 200,
  unplannedLabel: 'Nicht verplant',
  categories: [
    { id: 1, name: 'Wohnen', target: 800, actual: 0, difference: 800 },
    { id: 7, name: 'Lebensmittel', target: 480, actual: 0, difference: 480 },
  ],
  income: [],
}
// The month before "Gilt ab": the actuals shown as orientation (US2)
const previousDashboard = {
  ...dashboard,
  categories: [
    { id: 1, name: 'Wohnen', target: 800, actual: 812.5, difference: -12.5 },
    { id: 7, name: 'Lebensmittel', target: 480, actual: 0, difference: 480 },
  ],
  income: [{ id: 20, name: 'Gehalt/Lohn (netto)', actual: 3200 }],
}

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(
  onPut: () => Promise<Response> = () => json(categories[1]),
  dashboardData: unknown = dashboard,
) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === 'PUT') {
      return onPut()
    }
    if (url.startsWith('/api/dashboard?month=2026-10')) {
      return json(previousDashboard)
    }
    return url.startsWith('/api/dashboard') ? json(dashboardData) : json(categories)
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PlanningPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

function putCalls(fetchMock: ReturnType<typeof renderPage>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')
}

function rowNames() {
  return screen.getAllByRole('form').map((form) => form.getAttribute('aria-label'))
}

describe('PlanningPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 15))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the active categories with one field and a unit switch each', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(rowNames()).toEqual(['Wohnen', 'Lebensmittel', 'Freizeit', 'Nebenjob'])
    expect(within(wohnen).getByRole('textbox', { name: 'Soll für Wohnen in Euro' })).toHaveValue('800,00')
    expect(within(wohnen).getByRole('button', { name: '€' })).toHaveAttribute('aria-pressed', 'true')
    const lebensmittel = screen.getByRole('form', { name: 'Lebensmittel' })
    expect(within(lebensmittel).getByRole('textbox', { name: 'Soll für Lebensmittel in Prozent' })).toHaveValue('15,00')
    expect(within(lebensmittel).getByRole('button', { name: '%' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(screen.getByRole('form', { name: 'Freizeit' })).getByText('kein Soll')).toBeInTheDocument()
  })

  // US2: a percent target shows its euro value from Ø income, calculated by the backend (R3)
  it('shows the euro value of a percent target', async () => {
    renderPage()

    const lebensmittel = await screen.findByRole('form', { name: 'Lebensmittel' })
    expect(await within(lebensmittel).findByText('entspricht 480,00 €')).toBeInTheDocument()
  })

  it('shows the actual of each category in the month before "Gilt ab"', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(await within(wohnen).findByText('812,50 €')).toBeInTheDocument()
    expect(within(wohnen).getByText('Ist im Oktober 2026:')).toBeInTheDocument()
    expect(screen.getByText('Ist im Oktober 2026', { selector: '.plan-head span' })).toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Freizeit' })).getByText('0,00 €')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-10', expect.anything())
  })

  // R24, decision 33
  it('hides categories with only income and shows them on request', async () => {
    renderPage()

    await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.queryByRole('form', { name: 'Gehalt/Lohn (netto)' })).not.toBeInTheDocument()
    expect(screen.getByText('1 Kategorie mit ausschließlich Einnahmen ausgeblendet: Gehalt/Lohn (netto)')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Einblenden' }))

    const gehalt = screen.getByRole('form', { name: 'Gehalt/Lohn (netto)' })
    expect(rowNames()).toEqual(['Wohnen', 'Lebensmittel', 'Freizeit', 'Gehalt/Lohn (netto)', 'Nebenjob'])
    // Income of the month before is shown as income
    expect(await within(gehalt).findByText('+3.200,00 €')).toHaveClass('positive')

    fireEvent.click(screen.getByRole('button', { name: 'Ausblenden' }))
    expect(screen.queryByRole('form', { name: 'Gehalt/Lohn (netto)' })).not.toBeInTheDocument()
  })

  it('has no switch for hidden categories when there are none', async () => {
    const fetchMock = vi.fn((url: string) =>
      url.startsWith('/api/dashboard') ? json(dashboard) : json(categories.filter((c) => !c.incomeOnly)),
    )
    vi.stubGlobal('fetch', fetchMock)
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <PlanningPage />
      </QueryClientProvider>,
    )

    await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.queryByRole('button', { name: 'Einblenden' })).not.toBeInTheDocument()
  })

  // R4, R23, US7a: planned against the average income of the months before
  it('shows the average income, planned targets and the unplanned rest in the bar', async () => {
    const fetchMock = renderPage()

    const unplanned = await screen.findByText('Nicht verplant', { selector: 'dt' })
    expect(unplanned.nextSibling).toHaveTextContent('200,00 €')
    expect(unplanned.nextSibling).not.toHaveClass('negative')
    expect(screen.getByText('ohne Budget')).toBeInTheDocument()
    expect(screen.getByText('Verplant', { selector: 'dt' }).nextSibling).toHaveTextContent('3.000,00 €')
    expect(screen.getByText('Ø Einnahmen', { selector: 'dt' }).nextSibling).toHaveTextContent('3.200,00 €')
    expect(screen.getByText('Februar 2026 – Oktober 2026, 9 Monate')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-11', expect.anything())
    expect(unplanned.closest('.plan-bar')).toBeInTheDocument()
  })

  it('shows an over-planned month in the warning color without minus sign', async () => {
    renderPage(undefined, { ...dashboard, expenses: { target: 3300 }, unplanned: -100, unplannedLabel: 'Überplant' })

    const overPlanned = await screen.findByText('Überplant', { selector: 'dt' })
    expect(overPlanned.nextSibling).toHaveTextContent(/^100,00\s€$/)
    expect(overPlanned.nextSibling).toHaveClass('negative')
    expect(screen.getByText('mehr verplant als Ø Einnahmen')).toBeInTheDocument()
  })

  it('says so when no earlier month counts for the average income yet', async () => {
    renderPage(undefined, {
      ...dashboard,
      averageIncome: null,
      averageIncomeMonths: 0,
      averageIncomeFrom: null,
      averageIncomeTo: null,
      unplanned: null,
      unplannedLabel: null,
      categories: [{ id: 1, name: 'Wohnen', target: 800, actual: 0, difference: 800 }],
    })

    expect(await screen.findByText(/Noch kein abgeschlossener Monat mit Einträgen/)).toBeInTheDocument()
    expect(screen.queryByText('Verplant', { selector: 'dt' })).not.toBeInTheDocument()
    // Without Ø income a percent target has no euro value
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).queryByText(/entspricht/)).not.toBeInTheDocument()
  })

  // US2: "Gilt ab", default the current month
  it('starts at the current month and loads the targets of the month chosen in "Gilt ab"', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/categories?month=2026-11')
    expect(screen.getByLabelText('Gilt ab', { selector: 'input' })).toHaveValue('2026-11')
    expect(screen.getByText(/für alle folgenden Monate/)).toHaveTextContent('Es gilt ab November 2026 für alle folgenden Monate')
    // Set in an earlier month: say so. Set in the chosen month: nothing extra.
    expect(within(wohnen).getByText(/gesetzt ab September 2026/)).toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).queryByText(/gesetzt ab/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/categories?month=2026-12', expect.anything()))
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-12', expect.anything())
    expect(await screen.findByText('Ist im November 2026', { selector: '.plan-head span' })).toBeInTheDocument()
  })

  it('has no save button', async () => {
    renderPage()

    await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
  })

  it('rejects a negative euro target without sending a request', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(wohnen).getByRole('textbox')
    fireEvent.change(input, { target: { value: '-5' } })
    fireEvent.focusOut(input)

    expect(await within(wohnen).findByText('Der Soll-Betrag darf nicht negativ sein.')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('rejects a percent above 100 without sending a request', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '120' } })
    fireEvent.focusOut(input)

    expect(await within(row).findByText('Der Prozentwert muss zwischen 0 und 100 % liegen.')).toBeInTheDocument()
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('saves a percent target as a fraction with four decimals and clears the euro value', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '33,33' } })
    fireEvent.focusOut(input)

    expect(await within(row).findByText('Gespeichert ab November 2026.')).toBeInTheDocument()
    const [url, init] = putCalls(fetchMock)[0]
    expect(url).toBe('/api/categories/7/target?month=2026-11')
    expect(JSON.parse(init?.body as string)).toEqual({ targetAmount: null, targetPercent: 0.3333 })
  })

  // US2: only the chosen value is saved, the other one is cleared
  it('switches a euro target to percent and saves only the percent', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '25' } })
    const percent = within(row).getByRole('button', { name: '%' })
    fireEvent.focusOut(input, { relatedTarget: percent })
    fireEvent.click(percent)

    expect(percent).toHaveAttribute('aria-pressed', 'true')
    expect(within(row).getByRole('textbox', { name: 'Soll für Wohnen in Prozent' })).toHaveValue('25')
    expect(putCalls(fetchMock)).toHaveLength(0)

    fireEvent.focusOut(percent)

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
    expect(JSON.parse(putCalls(fetchMock)[0][1]?.body as string)).toEqual({ targetAmount: null, targetPercent: 0.25 })
  })

  it('saves a switched unit even when the number stays the same', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Nebenjob' })
    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    fireEvent.focusOut(within(row).getByRole('button', { name: '%' }))

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
    expect(JSON.parse(putCalls(fetchMock)[0][1]?.body as string)).toEqual({ targetAmount: null, targetPercent: 0.1 })
  })

  it('does not save a unit switch on an empty field', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Freizeit' })
    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    fireEvent.focusOut(within(row).getByRole('button', { name: '%' }))

    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  // US2: an empty field means "no target from this month"
  it('clears the target when the field is emptied', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '' } })
    fireEvent.focusOut(input)

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
    expect(JSON.parse(putCalls(fetchMock)[0][1]?.body as string)).toEqual({ targetAmount: null, targetPercent: null })
  })

  it('saves on Enter', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '850' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(await within(row).findByText('Gespeichert ab November 2026.')).toBeInTheDocument()
    expect(JSON.parse(putCalls(fetchMock)[0][1]?.body as string)).toEqual({ targetAmount: 850, targetPercent: null })
  })

  it('does not save when nothing changed', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.focusOut(input)
    fireEvent.change(input, { target: { value: '800,00 ' } })
    fireEvent.focusOut(input)

    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('shows a server field error at the field', async () => {
    renderPage(() =>
      json(
        {
          status: 400,
          detail: 'Die Eingaben sind ungültig.',
          errors: [{ field: 'targetPercent', message: 'Der Prozentwert muss zwischen 0 und 100 % liegen.' }],
        },
        400,
      ),
    )

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '20' } })
    fireEvent.focusOut(input)

    await waitFor(() => expect(within(row).getByRole('textbox')).toHaveAttribute('aria-invalid', 'true'))
    expect(within(row).getByText('Der Prozentwert muss zwischen 0 und 100 % liegen.')).toBeInTheDocument()
  })
})
