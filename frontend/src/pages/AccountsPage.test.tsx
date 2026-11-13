import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AccountGroup } from '../api/types'
import { AccountsPage } from './AccountsPage'

// Fictional accounts, shaped like the backend responses.
const groups: AccountGroup[] = [
  {
    role: 'EVERYDAY',
    total: 1250,
    accounts: [
      { id: 1, name: 'Girokonto', iban: null, role: 'EVERYDAY', defaultAccount: true, archived: false, balance: 1250, statementDate: null, statementAmount: null },
    ],
  },
  {
    role: 'SAVINGS',
    total: 700,
    accounts: [
      { id: 2, name: 'Tagesgeld', iban: null, role: 'SAVINGS', defaultAccount: false, archived: false, balance: 700, statementDate: '2026-11-01', statementAmount: 690 },
    ],
  },
  { role: 'DEPOT', total: 0, accounts: [] },
]

function renderPage() {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    void init
    const body = url === '/api/accounts'
      ? groups
      : url.startsWith('/api/bank-accounts/1/balances')
        ? { appBalance: 1250, bankBalance: 1200, difference: -50 }
        : url === '/api/reserves/by-account'
          ? [
              {
                accountId: 2, accountName: 'Tagesgeld', saved: 1340, target: 2400, accountBalance: 1040, missing: 300,
                pots: [{ itemId: 5, name: 'Kfz-Versicherung', categoryId: 3, amount: 1200, monthly: 100, balance: 1000,
                  due: false, nextDue: '2027-03-15', paid: 0, fromPot: 0, shortfall: 0 }],
              },
            ]
          : url.startsWith('/api/plan')
            ? { items: [] }
            : []
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AccountsPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AccountsPage', () => {
  it('shows the accounts by role with their sums and the last bank balance', async () => {
    renderPage()

    const everyday = await screen.findByRole('region', { name: 'Alltag' })
    expect(everyday).toHaveTextContent('1.250,00 €')
    const saving = screen.getByRole('region', { name: 'Sparen' })
    expect(saving).toHaveTextContent('Tagesgeld')
    expect(saving).toHaveTextContent('Bank am 01.11.2026: 690,00 €')
    expect(screen.queryByRole('region', { name: 'Depot' })).not.toBeInTheDocument()
  })

  it('enters a bank balance and shows the difference without booking it', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Kontostand von Girokonto laut Bank eintragen' }))
    const dialog = screen.getByRole('dialog', { name: 'Kontostand Girokonto' })
    fireEvent.change(within(dialog).getByLabelText('Kontostand laut Bank'), { target: { value: '1.200,00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    expect(await within(dialog).findByRole('status')).toHaveTextContent('Differenz −50,00 €: fehlt eine Buchung?')
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/bank-accounts/1/balances')).toBe(true))
  })

  // 27f2: the reserves of an account at a glance, the single ones when opened
  it('shows the reserves per account with what is missing on the account', async () => {
    renderPage()
    const reserves = await screen.findByRole('region', { name: 'Rücklagen' })
    const row = await within(reserves).findByRole('button', { name: /Tagesgeld/ })
    expect(row).toHaveTextContent('1.340,00 € / 2.400,00 €')
    expect(row).toHaveTextContent('−300,00 €')
    expect(within(reserves).queryByText('Kfz-Versicherung')).not.toBeInTheDocument()
    fireEvent.click(row)
    expect(within(reserves).getByText('Kfz-Versicherung')).toBeInTheDocument()
  })
})
