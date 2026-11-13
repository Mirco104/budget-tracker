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
      { id: 1, name: 'Girokonto', iban: null, role: 'EVERYDAY', defaultAccount: true, archived: false, balance: 1250, statementDate: null, statementAmount: null, comfortAmount: null, belowComfort: false },
    ],
  },
  {
    role: 'SAVINGS',
    total: 700,
    accounts: [
      { id: 2, name: 'Tagesgeld', iban: null, role: 'SAVINGS', defaultAccount: false, archived: false, balance: 700, statementDate: '2026-11-01', statementAmount: 690, comfortAmount: 1000, belowComfort: true },
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
            : url === '/api/saving-goals' && (init?.method ?? 'GET') === 'GET'
              ? [{ id: 4, name: 'Urlaub', targetAmount: 2000, targetDate: '2027-06-30', saved: 500, monthly: 100 }]
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
  // 27f4 (decision 60): one card Konten with the accounts by Kontoart
  it('shows the accounts by role with their sums and the last bank balance', async () => {
    renderPage()

    const card = await screen.findByRole('region', { name: 'Konten' })
    const everyday = within(card).getByRole('group', { name: 'Alltag' })
    expect(everyday).toHaveTextContent('1.250,00 €')
    expect(everyday).toHaveTextContent('Standard')
    const saving = within(card).getByRole('group', { name: 'Sparen' })
    expect(saving).toHaveTextContent('Tagesgeld')
    expect(saving).toHaveTextContent('Bank am 01.11.2026: 690,00 €')
    expect(within(card).queryByRole('group', { name: 'Depot' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /Kontoart/ })).not.toBeInTheDocument()
  })

  it('enters a bank balance in the window of the account and shows the difference without booking it', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Girokonto bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Girokonto' })
    fireEvent.change(within(dialog).getByLabelText('Kontostand laut Bank (optional)'), { target: { value: '1.200,00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    expect(await within(dialog).findByRole('status')).toHaveTextContent('Differenz −50,00 €: fehlt eine Buchung?')
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/bank-accounts/1/balances')).toBe(true))
    expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/bank-accounts/1' && init?.method === 'PUT')).toBe(false)
  })

  it('changes the Kontoart in the window of the account', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Tagesgeld bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Tagesgeld' })
    fireEvent.change(within(dialog).getByLabelText('Kontoart'), { target: { value: 'DEPOT' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/bank-accounts/2' && init?.method === 'PUT')
    expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({ name: 'Tagesgeld', role: 'DEPOT' })
  })

  // 27f2: the reserves of an account at a glance, the single ones when opened; 27f4: the coverage in words
  it('shows the reserves per account with what is missing on the account', async () => {
    renderPage()
    const reserves = await screen.findByRole('region', { name: 'Rücklagen' })
    const row = await within(reserves).findByRole('button', { name: /Tagesgeld/ })
    expect(row).toHaveTextContent('1.340,00 € / 2.400,00 €')
    expect(reserves).toHaveTextContent('Auf Tagesgeld fehlen 300,00 €')
    expect(within(reserves).queryByText('Kfz-Versicherung')).not.toBeInTheDocument()
    fireEvent.click(row)
    expect(within(reserves).getByText('Kfz-Versicherung')).toBeInTheDocument()
    expect(within(reserves).getByRole('columnheader', { name: 'Liegt auf' })).toBeInTheDocument()
    // 27f4 (decision 60): the everyday account may hold a reserve too
    expect(within(reserves).getByRole('option', { name: 'Girokonto' })).toBeInTheDocument()
  })

  // 27f4
  it('marks a balance below the comfort amount and changes the comfort amount', async () => {
    const fetchMock = renderPage()
    const card = await screen.findByRole('region', { name: 'Konten' })
    const saving = within(card).getByRole('group', { name: 'Sparen' })
    expect(saving).toHaveTextContent('unter Wohlfühlbetrag 1.000,00 €')
    expect(within(saving).getByText('700,00 €', { selector: '.item-amount' })).toHaveClass('below-comfort')
    expect(within(card).getByRole('group', { name: 'Alltag' })).not.toHaveTextContent('Wohlfühlbetrag')

    fireEvent.click(within(saving).getByRole('button', { name: 'Konto Tagesgeld bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Tagesgeld' })
    expect(within(dialog).getByLabelText('Wohlfühlbetrag (optional)')).toHaveValue('1000,00')
    fireEvent.change(within(dialog).getByLabelText('Wohlfühlbetrag (optional)'), { target: { value: '500' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/bank-accounts/2/comfort-amount')
    expect(call?.[1]?.body).toBe(JSON.stringify({ amount: 500 }))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/balances'))).toBe(false)
  })

  it('creates a new account with its Kontoart', async () => {
    const fetchMock = renderPage()
    const card = await screen.findByRole('region', { name: 'Konten' })
    fireEvent.click(within(card).getByRole('button', { name: '+ Neues Konto' }))
    const dialog = screen.getByRole('dialog', { name: 'Neues Konto' })
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Depot B' } })
    fireEvent.change(within(dialog).getByLabelText('Kontoart'), { target: { value: 'DEPOT' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/bank-accounts' && init?.method === 'POST')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ name: 'Depot B', iban: null, role: 'DEPOT' })
  })

  it('lists the saving goals and creates a new one', async () => {
    const fetchMock = renderPage()
    const goals = await screen.findByRole('region', { name: 'Sparziele' })
    await waitFor(() => expect(goals).toHaveTextContent('Urlaub'))
    expect(goals).toHaveTextContent('500,00 € / 2.000,00 €')
    expect(goals).toHaveTextContent('bis 30.06.2027')

    fireEvent.click(within(goals).getByRole('button', { name: '+ Neues Sparziel' }))
    const dialog = screen.getByRole('dialog', { name: 'Neues Sparziel' })
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Fahrrad' } })
    fireEvent.change(within(dialog).getByLabelText('Zielbetrag (optional)'), { target: { value: '800' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/saving-goals' && init?.method === 'POST')
    expect(call?.[1]?.body).toBe(JSON.stringify({ name: 'Fahrrad', targetAmount: 800, targetDate: null }))
  })
})
