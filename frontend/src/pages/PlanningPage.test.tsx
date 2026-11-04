import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { PlanningPage } from './PlanningPage'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', sortOrder: 1, targetAmount: 800, targetPercent: null, targetValidFrom: '2026-09', archived: false, incomeOnly: false },
  { id: 7, name: 'Lebensmittel', sortOrder: 7, targetAmount: null, targetPercent: 0.15, targetValidFrom: '2026-11', archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt/Lohn (netto)', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  // US10: archived categories are not planned
  { id: 30, name: 'Kredite', sortOrder: 30, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: true, incomeOnly: false },
]

// Only the fields the planning summary uses.
const average = { averageIncome: 3200, averageIncomeMonths: 9, averageIncomeFrom: '2026-02', averageIncomeTo: '2026-10' }
const dashboard = { incomeTotal: 1000, ...average, expenses: { target: 3000 }, unplanned: 200, unplannedLabel: 'Nicht verplant' }

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
    return url.startsWith('/api/dashboard') ? json(dashboardData) : json(categories)
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PlanningPage month="2026-11" />
    </QueryClientProvider>,
  )
  return fetchMock
}

function putCalls(fetchMock: ReturnType<typeof renderPage>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')
}

describe('PlanningPage', () => {
  it('shows all categories as one list with prefilled targets', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.getAllByRole('form').map((form) => form.getAttribute('aria-label'))).toEqual([
      'Wohnen',
      'Lebensmittel',
      'Gehalt/Lohn (netto)',
    ])
    expect(screen.queryByRole('heading', { name: 'Fixkosten' })).not.toBeInTheDocument()
    expect(within(wohnen).getByLabelText(/Soll \(€\)/)).toHaveValue('800,00')
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).getByLabelText(/Soll \(%\)/)).toHaveValue('15,00')
    // R3: every category can have a target, also an income category
    expect(within(screen.getByRole('form', { name: 'Gehalt/Lohn (netto)' })).getByLabelText(/Soll \(€\)/)).toHaveValue('')
  })

  // R4, R23: planned against the average income of the months before, not the income of the month
  it('shows the average income, planned targets and the unplanned rest', async () => {
    const fetchMock = renderPage()

    const unplanned = await screen.findByText('Nicht verplant', { selector: 'dt' })
    expect(unplanned.nextSibling).toHaveTextContent('200,00 €')
    expect(unplanned.nextSibling).not.toHaveClass('negative')
    expect(screen.getByText('Verplant', { selector: 'dt' }).nextSibling).toHaveTextContent('3.000,00 €')
    const average = screen.getByText('Ø Einnahmen', { selector: 'dt' })
    expect(average.nextSibling).toHaveTextContent('3.200,00 €')
    expect(screen.getByText('Februar 2026 – Oktober 2026, 9 Monate')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-11', expect.anything())
    expect(screen.queryByText(/Noch kein abgeschlossener Monat/)).not.toBeInTheDocument()
  })

  it('shows an over-planned month in red without minus sign', async () => {
    renderPage(undefined, { incomeTotal: 3200, ...average, expenses: { target: 3300 }, unplanned: -100, unplannedLabel: 'Überplant' })

    const overPlanned = await screen.findByText('Überplant', { selector: 'dt' })
    expect(overPlanned.nextSibling).toHaveTextContent(/^100,00\s€$/)
    expect(overPlanned.nextSibling).toHaveClass('negative')
  })

  it('says so when no earlier month counts for the average income yet', async () => {
    renderPage(undefined, {
      incomeTotal: 2000,
      averageIncome: null,
      averageIncomeMonths: 0,
      averageIncomeFrom: null,
      averageIncomeTo: null,
      expenses: { target: 800 },
      unplanned: null,
      unplannedLabel: null,
    })

    expect(await screen.findByText(/Noch kein abgeschlossener Monat mit Einträgen/)).toBeInTheDocument()
    expect(screen.queryByText('Verplant', { selector: 'dt' })).not.toBeInTheDocument()
  })

  it('loads the targets of the selected month and says from when they apply', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/categories?month=2026-11')
    expect(screen.getByText(/für alle folgenden Monate/)).toHaveTextContent('Gilt ab November 2026 für alle folgenden Monate')
    // Set in an earlier month: say so. Set in the selected month: nothing extra.
    expect(within(wohnen).getByText('Gesetzt ab September 2026.')).toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).queryByText(/Gesetzt ab/)).not.toBeInTheDocument()
  })

  it('has no save button', async () => {
    renderPage()

    await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
  })

  it('rejects a negative euro target without sending a request', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(wohnen).getByLabelText(/Soll \(€\)/)
    fireEvent.change(input, { target: { value: '-5' } })
    fireEvent.focusOut(input)

    expect(await within(wohnen).findByText('Der Soll-Betrag darf nicht negativ sein.')).toBeInTheDocument()
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('saves a percent target as a fraction with four decimals when the row loses focus', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    const input = within(row).getByLabelText(/Soll \(%\)/)
    fireEvent.change(input, { target: { value: '33,33' } })
    fireEvent.focusOut(input)

    expect(await within(row).findByText(/Gespeichert/)).toBeInTheDocument()
    const [url, init] = putCalls(fetchMock)[0]
    expect(url).toBe('/api/categories/7/target?month=2026-11')
    expect(JSON.parse(init?.body as string)).toEqual({ targetAmount: null, targetPercent: 0.3333 })
  })

  it('saves on Enter', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const input = within(row).getByLabelText(/Soll \(€\)/)
    fireEvent.change(input, { target: { value: '850' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(await within(row).findByText('Gespeichert ab November 2026.')).toBeInTheDocument()
    expect(JSON.parse(putCalls(fetchMock)[0][1]?.body as string)).toEqual({ targetAmount: 850, targetPercent: null })
  })

  it('does not save while the focus moves within the row or when nothing changed', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Wohnen' })
    const amount = within(row).getByLabelText(/Soll \(€\)/)
    const percent = within(row).getByLabelText(/Soll \(%\)/)
    fireEvent.focusOut(amount)
    fireEvent.change(amount, { target: { value: '850' } })
    fireEvent.focusOut(amount, { relatedTarget: percent })

    expect(putCalls(fetchMock)).toHaveLength(0)

    fireEvent.focusOut(percent)
    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
  })

  it('shows a server field error at the matching input', async () => {
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
    const input = within(row).getByLabelText(/Soll \(%\)/)
    fireEvent.change(input, { target: { value: '20' } })
    fireEvent.focusOut(input)

    await waitFor(() =>
      expect(within(row).getByLabelText(/Soll \(%\)/)).toHaveAttribute('aria-invalid', 'true'),
    )
    expect(within(row).getByText('Der Prozentwert muss zwischen 0 und 100 % liegen.')).toBeInTheDocument()
  })
})
