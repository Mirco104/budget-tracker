import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { SettingsPage } from './SettingsPage'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', type: 'EXPENSE', group: 'FIXED', sortOrder: 1, targetAmount: 800, targetPercent: null, system: false },
  { id: 7, name: 'Lebensmittel', type: 'EXPENSE', group: 'FLEXIBLE', sortOrder: 7, targetAmount: null, targetPercent: 0.15, system: false },
  { id: 15, name: 'Sonstiges (berechneter Rest)', type: 'EXPENSE', group: 'REMAINDER', sortOrder: 15, targetAmount: null, targetPercent: null, system: true },
  { id: 20, name: 'Gehalt/Lohn (netto)', type: 'INCOME', group: null, sortOrder: 20, targetAmount: null, targetPercent: null, system: false },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(onPut: () => Promise<Response> = () => json(categories[1])) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => (init?.method === 'PUT' ? onPut() : json(categories)))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SettingsPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

function putCalls(fetchMock: ReturnType<typeof renderPage>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')
}

describe('SettingsPage', () => {
  it('shows categories by group with prefilled targets and a read-only remainder', async () => {
    renderPage()

    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.getByRole('heading', { name: 'Fixkosten' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Einnahmen' })).toBeInTheDocument()
    expect(within(wohnen).getByLabelText(/Soll \(€\)/)).toHaveValue('800,00')
    expect(within(screen.getByRole('form', { name: 'Lebensmittel' })).getByLabelText(/Soll \(%\)/)).toHaveValue('15,00')

    expect(screen.queryByRole('form', { name: /Sonstiges/ })).not.toBeInTheDocument()
    expect(screen.getByText(/wird automatisch berechnet/)).toBeInTheDocument()
    expect(screen.getByText('Gehalt/Lohn (netto)')).toBeInTheDocument()
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
    expect(url).toBe('/api/categories/7/target')
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
