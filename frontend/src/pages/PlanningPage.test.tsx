import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { PlanningPage } from './PlanningPage'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', group: 'FIXED', sortOrder: 1, targetAmount: 800, targetPercent: null, targetValidFrom: '2026-09' },
  { id: 7, name: 'Lebensmittel', group: 'FLEXIBLE', sortOrder: 7, targetAmount: null, targetPercent: 0.15, targetValidFrom: '2026-11' },
  { id: 20, name: 'Gehalt/Lohn (netto)', group: null, sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null },
]

// Only the fields the planning summary uses.
const dashboard = { incomeTotal: 3200, expenses: { target: 3000 }, unplanned: 200, unplannedLabel: 'Nicht verplant' }

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
  it('shows categories by group with prefilled targets', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.getByRole('heading', { name: 'Fixkosten' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ohne Gruppe' })).toBeInTheDocument()
    expect(within(wohnen).getByLabelText(/Soll \(€\)/)).toHaveValue('800,00')
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).getByLabelText(/Soll \(%\)/)).toHaveValue('15,00')

    expect(screen.getByText('Gehalt/Lohn (netto)')).toBeInTheDocument()
  })

  it('shows income, planned targets and the unplanned rest of the month', async () => {
    const fetchMock = renderPage()

    const unplanned = await screen.findByText('Nicht verplant', { selector: 'dt' })
    expect(unplanned.nextSibling).toHaveTextContent('200,00 €')
    expect(unplanned.nextSibling).not.toHaveClass('negative')
    expect(screen.getByText('Verplant', { selector: 'dt' }).nextSibling).toHaveTextContent('3.000,00 €')
    expect(screen.getByText('Einnahmen November 2026').nextSibling).toHaveTextContent('3.200,00 €')
    expect(fetchMock).toHaveBeenCalledWith('/api/dashboard?month=2026-11', expect.anything())
    expect(screen.queryByText(/noch keine Einnahmen gebucht/)).not.toBeInTheDocument()
  })

  it('shows an over-planned month in red without minus sign', async () => {
    renderPage(undefined, { incomeTotal: 3200, expenses: { target: 3300 }, unplanned: -100, unplannedLabel: 'Überplant' })

    const overPlanned = await screen.findByText('Überplant', { selector: 'dt' })
    expect(overPlanned.nextSibling).toHaveTextContent(/^100,00\s€$/)
    expect(overPlanned.nextSibling).toHaveClass('negative')
  })

  it('says so when the month has no income yet', async () => {
    renderPage(undefined, { incomeTotal: 0, expenses: { target: 800 }, unplanned: -800, unplannedLabel: 'Überplant' })

    expect(await screen.findByText(/noch keine Einnahmen gebucht/)).toBeInTheDocument()
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

  it('rejects a negative euro target without sending a request', async () => {
    const fetchMock = renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    fireEvent.change(within(wohnen).getByLabelText(/Soll \(€\)/), { target: { value: '-5' } })
    fireEvent.click(within(wohnen).getByRole('button', { name: 'Speichern' }))

    expect(await within(wohnen).findByText('Der Soll-Betrag darf nicht negativ sein.')).toBeInTheDocument()
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('sends a percent target as a fraction with four decimals', async () => {
    const fetchMock = renderPage()

    const row = await screen.findByRole('form', { name: 'Lebensmittel' })
    fireEvent.change(within(row).getByLabelText(/Soll \(%\)/), { target: { value: '33,33' } })
    fireEvent.click(within(row).getByRole('button', { name: 'Speichern' }))

    expect(await within(row).findByText(/Gespeichert/)).toBeInTheDocument()
    const [url, init] = putCalls(fetchMock)[0]
    expect(url).toBe('/api/categories/7/target?month=2026-11')
    expect(JSON.parse(init?.body as string)).toEqual({ targetAmount: null, targetPercent: 0.3333 })
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
    fireEvent.click(within(row).getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(within(row).getByLabelText(/Soll \(%\)/)).toHaveAttribute('aria-invalid', 'true'),
    )
    expect(within(row).getByText('Der Prozentwert muss zwischen 0 und 100 % liegen.')).toBeInTheDocument()
  })
})
