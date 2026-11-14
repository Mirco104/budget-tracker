import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppNotification } from '../api/notifications'
import { Bell } from './Bell'
import { ClosedMonthCard } from './ClosedMonthCard'

// Fictional values, shaped like the backend responses.
const empty = {
  month: null, amount: null, accountId: null, accountName: null, balance: null, expected: null, limit: null,
  itemId: null, itemName: null, oldAmount: null, newAmount: null,
}
const notifications: AppNotification[] = [
  { ...empty, key: 'closing:2026-10', kind: 'MONTH_CLOSING', month: '2026-10', amount: 180 },
  { ...empty, key: 'limit:1:2026-11', kind: 'LOWER_LIMIT', accountId: 1, accountName: 'Girokonto', balance: 800, expected: -120, limit: 0 },
  { ...empty, key: 'price:k:-14.99', kind: 'PRICE_CHANGE', itemId: 9, itemName: 'Streaming', oldAmount: -12.99, newAmount: -14.99 },
  { ...empty, key: 'overplanned:2026-11:189.93', kind: 'OVERPLANNED', month: '2026-11', amount: 189.93 },
]

function renderWith(ui: React.ReactNode, items: AppNotification[] = notifications) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const body = init?.method
      ? url.startsWith('/api/month-closings')
        ? { month: '2026-10', leftover: 180, closed: true, savingGoalId: 4, savingGoalName: 'Urlaub', accounts: [] }
        : null
      : url === '/api/notifications'
        ? items
        : url === '/api/saving-goals'
          ? [{ id: 4, name: 'Urlaub', targetAmount: null, targetDate: null, saved: 0, monthly: 0 }]
          : url.startsWith('/api/month-closings')
            ? {
                month: '2026-10', leftover: -40, closed: false, savingGoalId: null, savingGoalName: null,
                accounts: [{ accountId: 1, name: 'Girokonto', change: -40 }, { accountId: 2, name: 'Tagesgeld', change: 300 }],
              }
            : []
    return Promise.resolve(new Response(body === null ? null : JSON.stringify(body), { status: body === null ? 204 : 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>,
  )
  return fetchMock
}

function sent(fetchMock: ReturnType<typeof vi.fn>, url: string) {
  const call = fetchMock.mock.calls.find(([u, init]) => u === url && (init as RequestInit | undefined)?.method === 'POST')
  return call ? JSON.parse(String((call[1] as RequestInit).body ?? 'null')) : undefined
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Bell', () => {
  it('counts the notifications and lists them in words', async () => {
    renderWith(<Bell />)
    const bell = await screen.findByRole('button', { name: 'Benachrichtigungen (4)' })
    fireEvent.click(bell)
    const panel = screen.getByRole('region', { name: 'Benachrichtigungen' })
    expect(panel).toHaveTextContent('Oktober 2026 abschließen: 180,00 € sind übrig geblieben. Wohin damit?')
    expect(panel).toHaveTextContent(
      'Girokonto fällt nach den erwarteten Zahlungen voraussichtlich auf -120,00 €, unter die Untergrenze von 0,00 €.',
    )
    expect(panel).toHaveTextContent('„Streaming“ kostet jetzt 14,99 € statt 12,99 €.')
    expect(panel).toHaveTextContent('Deine Planung für November 2026 ist um 189,93 € überplant.')
  })

  it('assigns the leftover to a saving goal or closes the month without one', async () => {
    const fetchMock = renderWith(<Bell />)
    fireEvent.click(await screen.findByRole('button', { name: 'Benachrichtigungen (4)' }))
    const panel = screen.getByRole('region', { name: 'Benachrichtigungen' })
    await within(panel).findByRole('option', { name: 'Urlaub' })
    fireEvent.click(within(panel).getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(sent(fetchMock, '/api/month-closings/2026-10')).toEqual({ savingGoalId: 4 }))

    fireEvent.click(within(panel).getByRole('button', { name: /Nicht zuordnen/ }))
    await waitFor(() =>
      expect(fetchMock.mock.calls.filter(([u]) => u === '/api/month-closings/2026-10').length).toBe(2),
    )
  })

  it('marks a notification as done and takes over a price change', async () => {
    const fetchMock = renderWith(<Bell />)
    fireEvent.click(await screen.findByRole('button', { name: 'Benachrichtigungen (4)' }))
    const panel = screen.getByRole('region', { name: 'Benachrichtigungen' })
    fireEvent.click(within(panel).getAllByRole('button', { name: 'Erledigt' })[0])
    await waitFor(() => expect(sent(fetchMock, '/api/notifications/done')).toEqual({ key: 'limit:1:2026-11' }))

    fireEvent.click(within(panel).getByRole('button', { name: 'Übernehmen' }))
    await waitFor(() =>
      expect(sent(fetchMock, '/api/notifications/price-change')).toEqual({ itemId: 9, amount: -14.99 }),
    )
  })

  it('says so when there is nothing', async () => {
    renderWith(<Bell />, [])
    fireEvent.click(await screen.findByRole('button', { name: 'Benachrichtigungen' }))
    expect(screen.getByText('Keine Benachrichtigungen.')).toBeInTheDocument()
  })
})

// Decision 59: a past month with its shortfall and the change of each account
describe('ClosedMonthCard', () => {
  it('shows the shortfall and per account the change of the balance', async () => {
    renderWith(<ClosedMonthCard month="2026-10" />)
    const head = await screen.findByRole('button', { name: /Fehlbetrag im Oktober/ })
    expect(head).toHaveTextContent('−40,00 €')
    fireEvent.click(head)
    const card = screen.getByRole('region', { name: 'Fehlbetrag' })
    expect(card).toHaveTextContent('Girokonto−40,00 €')
    expect(card).toHaveTextContent('Tagesgeld+300,00 €')
    // A shortfall is not assigned to a goal
    expect(within(card).queryByRole('button', { name: 'Zuordnen' })).not.toBeInTheDocument()
  })
})
