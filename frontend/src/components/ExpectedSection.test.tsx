import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Term } from '../api/types'
import { ExpectedSection } from './ExpectedSection'

// Fictional terms, shaped like the backend responses.
function term(overrides: Partial<Term>): Term {
  return {
    itemId: 1,
    itemKey: 'k1',
    name: 'Miete',
    date: '2026-11-01',
    dueDate: '2026-11-02',
    amount: -900,
    categoryId: 1,
    categoryName: 'Wohnen',
    accountId: 1,
    entryMode: 'AUTO',
    group: 'FIXED',
    status: 'EXPECTED',
    transactionId: null,
    linked: false,
    ...overrides,
  }
}

const terms: Term[] = [
  term({ itemId: 1, name: 'Miete', dueDate: '2026-11-30', date: '2026-11-30' }),
  term({ itemId: 2, itemKey: 'k2', name: 'Notgroschen', entryMode: 'TICK', group: 'SAVING', amount: -150, date: '2026-11-28', dueDate: '2026-11-28' }),
  term({ itemId: 3, itemKey: 'k3', name: 'Verein', entryMode: 'TICK', status: 'FULFILLED', linked: true, transactionId: 9 }),
  term({ itemId: 4, itemKey: 'k4', name: 'Strom', entryMode: 'AUTO', status: 'FULFILLED', linked: true, transactionId: 10 }),
  term({ itemId: 5, itemKey: 'k5', name: 'Fitness', entryMode: 'TICK', status: 'SKIPPED' }),
  term({ itemId: 6, itemKey: 'k6', name: 'Abo', entryMode: 'MANUAL', status: 'MISSED', dueDate: '2026-11-03' }),
]

function renderSection() {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    void init
    if (url.startsWith('/api/terms?')) {
      return Promise.resolve(new Response(JSON.stringify(terms), { status: 200 }))
    }
    return Promise.resolve(new Response(url === '/api/terms/paid' ? '{"id": 1}' : null, { status: url === '/api/terms/paid' ? 201 : 204 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ExpectedSection from="2026-11-01" to="2026-11-30" categories={[]} />
    </QueryClientProvider>,
  )
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ExpectedSection', () => {
  it('lists open, ticked-off and skipped terms, but not entered automatic ones', async () => {
    renderSection()
    const section = await screen.findByRole('region', { name: 'Erwartet' })

    expect(within(section).getByRole('heading')).toHaveTextContent('Erwartet bis 30.11.2026')
    expect(within(section).getByText('wird am 30.11.2026 eingetragen')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Notgroschen abhaken' })).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Abhaken von Verein rückgängig machen' })).toBeInTheDocument()
    expect(within(section).getByText('fällt diesen Monat weg')).toBeInTheDocument()
    expect(within(section).getByText('Überfällig seit 03.11.')).toBeInTheDocument()
    expect(within(section).queryByText('Strom')).not.toBeInTheDocument()
    // An automatic item has no circle
    expect(within(section).queryByRole('button', { name: 'Miete abhaken' })).not.toBeInTheDocument()
  })

  it('ticks off a term and offers to undo it', async () => {
    const fetchMock = renderSection()
    fireEvent.click(await screen.findByRole('button', { name: 'Notgroschen abhaken' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/terms/paid')).toBe(true))
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/terms/paid')
    expect(JSON.parse(call![1]!.body as string)).toEqual({ itemId: 2, date: '2026-11-28' })
    fireEvent.click(await screen.findByRole('button', { name: 'Rückgängig' }))
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/terms/paid?itemId=2&date=2026-11-28' && init?.method === 'DELETE')).toBe(true),
    )
  })

  it('skips a term from its dialog', async () => {
    const fetchMock = renderSection()
    fireEvent.click(await screen.findByText('Abo'))
    const dialog = await screen.findByRole('dialog', { name: 'Abo' })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Diesen Monat fällt es weg' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/terms/skip')).toBe(true))
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/terms/skip')
    expect(JSON.parse(call![1]!.body as string)).toEqual({ itemId: 6, date: '2026-11-01' })
  })
})
