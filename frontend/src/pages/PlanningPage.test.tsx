import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { PlanningPage } from './PlanningPage'

const noFixedCosts = { fixedCosts: null, fixedCostItems: [] }

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  {
    id: 1, name: 'Wohnen', icon: 'home', sortOrder: 1, targetAmount: 800, targetValidFrom: '2026-09', archived: false,
    incomeOnly: false,
    // R25: 900 a month and 600 a year, 950 in total
    fixedCosts: 950,
    fixedCostItems: [
      { recurringId: 11, description: 'Miete', amount: 900, interval: 1, monthly: 900 },
      { recurringId: 12, description: 'Hausrat', amount: 600, interval: 12, monthly: 50 },
    ],
  },
  { id: 7, name: 'Lebensmittel', icon: 'cart', sortOrder: 7, targetAmount: 480, targetValidFrom: '2026-11', archived: false, incomeOnly: false, ...noFixedCosts },
  {
    id: 8, name: 'Freizeit', icon: 'tag', sortOrder: 8, targetAmount: null, targetValidFrom: null, archived: false,
    incomeOnly: false, fixedCosts: 15,
    fixedCostItems: [{ recurringId: 13, description: 'Streaming', amount: 15, interval: 1, monthly: 15 }],
  },
  // R24: only positive entries and no target, hidden by default
  { id: 20, name: 'Gehalt/Lohn (netto)', icon: 'wallet', sortOrder: 20, targetAmount: null, targetValidFrom: null, archived: false, incomeOnly: true, ...noFixedCosts },
  // R24: income only, but with a target in the month, so it stays
  { id: 21, name: 'Nebenjob', icon: 'tag', sortOrder: 21, targetAmount: 10, targetValidFrom: '2026-11', archived: false, incomeOnly: true, ...noFixedCosts },
  // US10: archived categories are not planned
  { id: 30, name: 'Kredite', icon: 'tag', sortOrder: 30, targetAmount: null, targetValidFrom: null, archived: true, incomeOnly: false, ...noFixedCosts },
]

// Only the fields the planning page uses.
const average = { averageIncome: 3200, averageIncomeMonths: 9, averageIncomeFrom: '2026-02', averageIncomeTo: '2026-10' }
const dashboard = {
  incomeTotal: 1000,
  ...average,
  minimumIncome: 2400,
  expenses: { target: 3000 },
  unplanned: 200,
  unplannedLabel: 'Nicht verplant',
  unplannedMinimum: -600,
  unplannedMinimumLabel: 'Überplant',
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
const withoutAverage = {
  ...dashboard,
  averageIncome: null,
  minimumIncome: null,
  averageIncomeMonths: 0,
  averageIncomeFrom: null,
  averageIncomeTo: null,
  unplanned: null,
  unplannedLabel: null,
  unplannedMinimum: null,
  unplannedMinimumLabel: null,
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

function putBody(fetchMock: ReturnType<typeof renderPage>, index = 0) {
  return JSON.parse(putCalls(fetchMock)[index][1]?.body as string)
}

function rowNames() {
  return screen.getAllByRole('form').map((form) => form.getAttribute('aria-label'))
}

/** The tile in the bar whose label is the given text. */
function tile(label: string) {
  return screen.getByText(label, { selector: '.tile-label' }).closest('.plan-tile') as HTMLElement
}

function equivalent(row: HTMLElement) {
  return row.querySelector('.plan-equivalent')
}

describe('PlanningPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 15))
    localStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the active categories with their target in euro and a unit switch each', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(rowNames()).toEqual(['Wohnen', 'Lebensmittel', 'Freizeit', 'Nebenjob'])
    expect(within(wohnen).getByRole('textbox', { name: 'Soll für Wohnen in Euro' })).toHaveValue('800,00')
    expect(within(wohnen).getByRole('button', { name: '€' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).getByRole('textbox')).toHaveValue('480,00')
    expect(within(screen.getByRole('form', { name: 'Freizeit' })).getByText('kein Soll')).toBeInTheDocument()
  })

  // US2, decision 36: the other value is always below the field
  it('shows the percent of the base below a euro target', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    await waitFor(() => expect(equivalent(wohnen)).toHaveTextContent('≙ 25 % der Ø Einnahmen'))
    expect(equivalent(screen.getByRole('form', { name: 'Lebensmittel' }))).toHaveTextContent('≙ 15 % der Ø Einnahmen')
    expect(equivalent(screen.getByRole('form', { name: 'Freizeit' }))).toBeNull()
  })

  it('converts the number when the unit is switched and saves nothing for it', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    await waitFor(() => expect(equivalent(row)).not.toBeNull())
    const percent = within(row).getByRole('button', { name: '%' })
    fireEvent.click(percent)

    expect(percent).toHaveAttribute('aria-pressed', 'true')
    expect(within(row).getByRole('textbox', { name: 'Soll für Wohnen in Prozent der Ø Einnahmen' })).toHaveValue('25')
    expect(equivalent(row)).toHaveTextContent('≙ 800,00 €, wird so gespeichert')

    fireEvent.click(within(row).getByRole('button', { name: '€' }))
    expect(within(row).getByRole('textbox')).toHaveValue('800,00')

    fireEvent.focusOut(within(row).getByRole('textbox'))
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  // 950 € are 29,69 % of 3200 €, which would be 950,08 €: switching alone must not change the target
  it('keeps the exact euro value while the converted percent is not edited', async () => {
    const fetchMock = renderPage(() => json({ ...categories[1], targetAmount: 950 }))

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '950' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))

    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    expect(within(row).getByRole('textbox')).toHaveValue('29,69')
    expect(equivalent(row)).toHaveTextContent('≙ 950,00 €')
    fireEvent.click(screen.getByRole('button', { name: /^Mindesteinnahmen/ }))
    expect(equivalent(row)).toHaveTextContent('≙ 950,00 €')
    fireEvent.focusOut(within(row).getByRole('textbox'))
    fireEvent.click(within(row).getByRole('button', { name: '€' }))

    expect(within(row).getByRole('textbox')).toHaveValue('950,00')
    expect(putCalls(fetchMock)).toHaveLength(1)
  })

  it('saves a percent input as euro of the base', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    await waitFor(() => expect(equivalent(row)).not.toBeNull())
    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '33,33' } })
    fireEvent.focusOut(input)

    expect(await within(row).findByText('Gespeichert ab November 2026.')).toBeInTheDocument()
    const [url] = putCalls(fetchMock)[0]
    expect(url).toBe('/api/categories/7/target?month=2026-11')
    // 33.33 % of 3200.00
    expect(putBody(fetchMock)).toEqual({ targetAmount: 1066.56 })
    expect(equivalent(row)).toHaveTextContent('≙ 1.066,56 €')
  })

  // Regression: the equivalent stays after saving (it used to appear only after a change of the month)
  it('keeps the equivalent next to the saved note', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '850' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(await within(row).findByText('Gespeichert ab November 2026.')).toBeInTheDocument()
    expect(putBody(fetchMock)).toEqual({ targetAmount: 850 })
    // 850 / 3200 = 26.5625 %
    expect(equivalent(row)).toHaveTextContent('≙ 26,56 % der Ø Einnahmen')
  })

  // US7a, decision 36: a click on a tile makes it the base
  it('uses the minimum income as base when its tile is clicked', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    const average = await screen.findByRole('button', { name: /^Ø Einnahmen/ })
    const minimum = screen.getByRole('button', { name: /^Mindesteinnahmen/ })
    expect(average).toHaveAttribute('aria-pressed', 'true')
    expect(minimum).toHaveAttribute('aria-pressed', 'false')
    expect(within(average).getByText('Basis')).toBeInTheDocument()
    expect(within(minimum).getByText('als Basis wählen')).toBeInTheDocument()
    expect(tile('Nicht verplant')).toHaveTextContent('200,00 €')

    fireEvent.click(minimum)

    expect(minimum).toHaveAttribute('aria-pressed', 'true')
    expect(tile('Überplant')).toHaveTextContent('600,00 €')
    expect(tile('Überplant')).toHaveClass('over')
    expect(screen.getByText('mehr verplant als Mindesteinnahmen')).toBeInTheDocument()
    expect(tile('Verplant')).toHaveTextContent('125 % der Mindesteinnahmen')
    // 800 / 2400
    expect(equivalent(wohnen)).toHaveTextContent('≙ 33,33 % der Mindesteinnahmen')
    expect(localStorage.getItem('planning-base')).toBe('min')
  })

  it('keeps the euro value of a percent input when the base changes', async () => {
    renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    await waitFor(() => expect(equivalent(row)).not.toBeNull())
    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    expect(within(row).getByRole('textbox')).toHaveValue('25')

    fireEvent.click(screen.getByRole('button', { name: /^Mindesteinnahmen/ }))

    expect(within(row).getByRole('textbox')).toHaveValue('33,33')
    expect(equivalent(row)).toHaveTextContent('≙ 800,00 €')
  })

  it('starts with the base chosen before in this browser', async () => {
    localStorage.setItem('planning-base', 'min')
    renderPage()

    expect(await screen.findByRole('button', { name: /^Mindesteinnahmen/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows the Ø and minimum income, planned targets and the unplanned rest in the bar', async () => {
    const fetchMock = renderPage()

    await screen.findByRole('button', { name: /^Ø Einnahmen/ })
    expect(tile('Ø Einnahmen')).toHaveTextContent('3.200,00 €')
    expect(tile('Mindesteinnahmen')).toHaveTextContent('2.400,00 €')
    expect(tile('Verplant')).toHaveTextContent('3.000,00 €')
    expect(tile('Verplant')).toHaveTextContent('93,8 % der Ø Einnahmen')
    expect(tile('Nicht verplant')).not.toHaveClass('over')
    expect(screen.getByText('ohne Budget')).toBeInTheDocument()
    expect(screen.getAllByText('Februar 2026 – Oktober 2026, 9 Monate')).toHaveLength(2)
    expect(screen.getByText(/Ø und Mindesteinnahmen stammen aus Februar 2026 – Oktober 2026/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-11', expect.anything())
    expect(tile('Verplant').closest('.plan-bar')).toBeInTheDocument()
  })

  it('says so and allows only euro when no earlier month counts yet', async () => {
    renderPage(undefined, withoutAverage)

    expect(await screen.findByText(/Noch kein abgeschlossener Monat mit Einträgen/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Ø Einnahmen/ })).not.toBeInTheDocument()
    const wohnen = screen.getByRole('form', { name: 'Wohnen' })
    expect(within(wohnen).getByRole('button', { name: '%' })).toBeDisabled()
    expect(equivalent(wohnen)).toHaveTextContent('≙ – %')
  })

  // R25, US2: fixed costs below the name, expandable, with "übernehmen"
  it('shows the fixed costs of a category and lists its recurring expenses on request', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    const toggle = within(wohnen).getByRole('button', { name: /fix 950,00\s€/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(within(wohnen).queryByText('Miete')).not.toBeInTheDocument()

    fireEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const items = within(wohnen).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Miete900,00 € / Monat')
    expect(items[1]).toHaveTextContent('Hausrat50,00 € / Monat600,00 € jährlich')
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).queryByRole('button', { name: /fix/ }))
      .not.toBeInTheDocument()
  })

  it('warns when the target is below the fixed costs and takes them over on request', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(wohnen).toHaveClass('short')
    expect(within(wohnen).getByText('unter den fixen Kosten von 950,00 €')).toBeInTheDocument()

    fireEvent.click(within(wohnen).getByRole('button', { name: /^Fixe Kosten 950,00\s€ als Soll für Wohnen übernehmen$/ }))

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
    expect(putBody(fetchMock)).toEqual({ targetAmount: 950 })
    expect(within(wohnen).getByRole('textbox')).toHaveValue('950,00')
    expect(wohnen).not.toHaveClass('short')
    expect(within(wohnen).queryByRole('button', { name: /übernehmen/ })).not.toBeInTheDocument()
  })

  it('offers the fixed costs for a category without target', async () => {
    renderPage()

    const freizeit = await screen.findByRole('form', { name: 'Freizeit' })
    expect(freizeit).not.toHaveClass('short')
    expect(within(freizeit).getByRole('button', { name: /übernehmen/ })).toBeInTheDocument()
  })

  it('shows the actual of each category in the month before "Gilt ab"', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    await waitFor(() => expect(wohnen.querySelector('.plan-previous')).toHaveTextContent('812,50 €'))
    expect(wohnen.querySelector('.plan-previous')).toHaveTextContent('Ist im Vormonat (Oktober): 812,50 €')
    expect(wohnen.querySelector('.plan-previous-mobile')).toHaveTextContent(/^Vormonat \(Okt\.?\): 812,50\s€$/)
    expect(screen.getByText('Ist im Vormonat (Oktober)', { selector: '.plan-head span' })).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Freizeit' }).querySelector('.plan-previous')).toHaveTextContent('0,00 €')
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
    expect((await within(gehalt).findAllByText('+3.200,00 €'))[0]).toHaveClass('positive')

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

  // US2: "Gilt ab", default the current month
  it('starts at the current month and loads the targets of the month chosen in "Gilt ab"', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/categories?month=2026-11')
    expect(screen.getByLabelText('Gilt ab', { selector: 'input' })).toHaveValue('2026-11')
    expect(screen.getByText(/für alle folgenden Monate/)).toHaveTextContent('Er gilt ab November 2026 für alle folgenden Monate')
    // Set in an earlier month: say so. Set in the chosen month: nothing extra.
    expect(within(wohnen).getByText('gesetzt ab September 2026')).toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).queryByText(/gesetzt ab/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/categories?month=2026-12', expect.anything()))
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-12', expect.anything())
    expect(await screen.findByText('Ist im Vormonat (November)', { selector: '.plan-head span' })).toBeInTheDocument()
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
    await waitFor(() => expect(equivalent(row)).not.toBeNull())
    fireEvent.click(within(row).getByRole('button', { name: '%' }))
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '120' } })
    fireEvent.focusOut(input)

    expect(await within(row).findByText('Der Prozentwert muss zwischen 0 und 100 % liegen.')).toBeInTheDocument()
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
    expect(putBody(fetchMock)).toEqual({ targetAmount: null })
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
          errors: [{ field: 'targetAmount', message: 'Der Soll-Betrag darf höchstens 2 Nachkommastellen haben.' }],
        },
        400,
      ),
    )

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    const input = within(row).getByRole('textbox')
    fireEvent.change(input, { target: { value: '20' } })
    fireEvent.focusOut(input)

    await waitFor(() => expect(within(row).getByRole('textbox')).toHaveAttribute('aria-invalid', 'true'))
    expect(within(row).getByText('Der Soll-Betrag darf höchstens 2 Nachkommastellen haben.')).toBeInTheDocument()
  })
})
