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
      { id: 1, name: 'Girokonto', iban: null, role: 'EVERYDAY', defaultAccount: true, archived: false, balance: 1250, statementDate: null, statementAmount: null, lowerLimitMode: 'ZERO', lowerLimit: 0, belowLimit: false },
    ],
  },
  {
    role: 'SAVINGS',
    total: 700,
    accounts: [
      { id: 2, name: 'Tagesgeld', iban: null, role: 'SAVINGS', defaultAccount: false, archived: false, balance: 700, statementDate: '2026-11-01', statementAmount: 690, lowerLimitMode: 'CUSTOM', lowerLimit: 1000, belowLimit: true },
    ],
  },
  { role: 'DEPOT', total: 0, accounts: [] },
]

function renderPage(start = false) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    void init
    const method = init?.method ?? 'GET'
    if (url.startsWith('/api/bank-accounts/1/balances/') && method === 'DELETE') {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    const body = url === '/api/accounts'
      ? groups
      : url === '/api/bank-accounts/1/balances' && method === 'GET'
        ? [
            { id: 8, date: '2026-11-10', amount: 1200, appBalance: 1250, difference: -50 },
            { id: 7, date: '2026-11-01', amount: 1000, appBalance: 1000, difference: 0 },
          ]
      : url.startsWith('/api/bank-accounts/1/balances')
        ? { appBalance: 1250, bankBalance: 1200, difference: -50, start }
        : url === '/api/reserves/by-account'
          ? [
              {
                accountId: 2, accountName: 'Tagesgeld', saved: 1340, target: 2400, accountBalance: 1040, missing: 300,
                pots: [{ itemId: 5, name: 'Kfz-Versicherung', categoryId: 3, amount: 1200, monthly: 100, balance: 1000,
                  due: false, nextDue: '2027-03-15', paid: 0, fromPot: 0, shortfall: 0 }],
              },
            ]
          : url === '/api/bank-accounts'
            ? [
                { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true, role: 'EVERYDAY' },
                { id: 2, name: 'Tagesgeld', iban: null, defaultAccount: false, archived: false, used: true, role: 'SAVINGS' },
                { id: 3, name: 'Altes Konto', iban: null, defaultAccount: false, archived: true, used: true, role: 'EVERYDAY' },
              ]
          : url === '/api/bank-accounts/1/merge'
            ? { account: { id: 2, name: 'Tagesgeld' }, movedTransactions: 3, deletedTransfers: 1 }
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

    const card = await screen.findByRole('region', { name: 'Bankkonten' })
    const everyday = within(card).getByRole('group', { name: 'Alltag' })
    expect(everyday).toHaveTextContent('1.250,00 €')
    expect(everyday).toHaveTextContent('Standard')
    const saving = within(card).getByRole('group', { name: 'Sparen' })
    expect(saving).toHaveTextContent('Tagesgeld')
    expect(saving).toHaveTextContent('Bank am 01.11.2026: 690,00 €')
    expect(within(card).queryByRole('group', { name: 'Depot' })).not.toBeInTheDocument()
    // 27f4: the total of all accounts
    expect(card).toHaveTextContent('Gesamt1.950,00 €')
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

  // Review 23.11.2026: the first balance of an account is its start, without a difference
  it('calls the first balance of an account its start', async () => {
    renderPage(true)
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Girokonto bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Girokonto' })
    fireEvent.change(within(dialog).getByLabelText('Kontostand laut Bank (optional)'), { target: { value: '1.200,00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    const status = await within(dialog).findByRole('status')
    expect(status).toHaveTextContent('Kontostand gespeichert. Er ist der Startwert dieses Kontos.')
    expect(status).not.toHaveTextContent('fehlt eine Buchung')
  })

  // Ticket #135: the history opens in its own window above the account; a wrong balance can be deleted
  it('shows the history of the bank balances and deletes a wrong one after confirming', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Girokonto bearbeiten' }))
    const account = screen.getByRole('dialog', { name: 'Konto Girokonto' })
    fireEvent.click(await within(account).findByRole('button', { name: 'Verlauf der Kontostände ansehen' }))

    const history = await screen.findByRole('dialog', { name: /Kontostände von Girokonto/ })
    const rows = within(await within(history).findByRole('list', { name: 'Kontostände' })).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('10.11.2026')
    expect(rows[0]).toHaveTextContent('1.200,00 €')
    expect(rows[0]).toHaveTextContent('App 1.250,00 € · Differenz −50,00 €')
    expect(rows[1]).toHaveTextContent('✓ stimmte mit der App überein')

    fireEvent.click(within(history).getByRole('button', { name: 'Kontostand vom 10.11.2026 löschen' }))
    const confirm = within(history).getByRole('group', { name: 'Löschen bestätigen' })
    fireEvent.click(within(confirm).getByRole('button', { name: 'Löschen' }))

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url, init]) => url === '/api/bank-accounts/1/balances/8' && init?.method === 'DELETE'),
      ).toBe(true),
    )
  })

  it('offers the history only for an account with bank balances', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Tagesgeld bearbeiten' }))
    const account = screen.getByRole('dialog', { name: 'Konto Tagesgeld' })

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => url === '/api/bank-accounts/2/balances')).toBe(true),
    )
    expect(within(account).queryByRole('button', { name: 'Verlauf der Kontostände ansehen' })).not.toBeInTheDocument()
  })

  // Ticket #139: an account goes into another one; an archived account is no target
  it('merges an account into another one after confirming', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Girokonto bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Girokonto' })

    fireEvent.click(await within(dialog).findByRole('button', { name: 'Zusammenführen …' }))
    const confirm = within(dialog).getByRole('group', { name: 'Zusammenführen' })
    const target = within(confirm).getByLabelText('In Konto')
    expect(Array.from((target as HTMLSelectElement).options).map((o) => o.text)).toEqual(['Konto wählen …', 'Tagesgeld'])
    expect(within(confirm).getByRole('button', { name: 'Zusammenführen' })).toBeDisabled()
    fireEvent.change(target, { target: { value: '2' } })
    expect(confirm).toHaveTextContent('gehen auf „Tagesgeld“ über')
    expect(confirm).toHaveTextContent('und das Zielkonto wird Standardkonto')
    fireEvent.click(within(confirm).getByRole('button', { name: 'Zusammenführen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/bank-accounts/1/merge')
    expect(call?.[1]?.method).toBe('POST')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ targetId: 2 })
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

  // P12 (27h): the lower limit replaces the comfort amount
  it('marks a balance below the lower limit and changes the lower limit', async () => {
    const fetchMock = renderPage()
    const card = await screen.findByRole('region', { name: 'Bankkonten' })
    const saving = within(card).getByRole('group', { name: 'Sparen' })
    expect(saving).toHaveTextContent('unter Untergrenze 1.000,00 €')
    expect(within(saving).getByText('700,00 €', { selector: '.item-amount' })).toHaveClass('below-limit')
    // 0 € is only named when the balance is below it
    expect(within(card).getByRole('group', { name: 'Alltag' })).not.toHaveTextContent('Untergrenze')

    fireEvent.click(within(saving).getByRole('button', { name: 'Konto Tagesgeld bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Tagesgeld' })
    expect(within(dialog).getByRole('combobox', { name: /Untergrenze/ })).toHaveValue('CUSTOM')
    expect(within(dialog).getByLabelText('Eigene Untergrenze')).toHaveValue('1000,00')
    expect(within(dialog).getByRole('button', { name: 'Erklärung: Was löst die Untergrenze aus?' })).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('Eigene Untergrenze'), { target: { value: '500' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/bank-accounts/2/lower-limit')
    expect(call?.[1]?.body).toBe(JSON.stringify({ mode: 'CUSTOM', amount: 500 }))
    // No bank balance was entered (the history is only read)
    expect(
      fetchMock.mock.calls.some(([url, init]) => String(url).includes('/balances') && init?.method === 'POST'),
    ).toBe(false)
  })

  it('switches the lower limit off', async () => {
    const fetchMock = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto Girokonto bearbeiten' }))
    const dialog = screen.getByRole('dialog', { name: 'Konto Girokonto' })
    expect(within(dialog).getByRole('combobox', { name: /Untergrenze/ })).toHaveValue('ZERO')
    fireEvent.change(within(dialog).getByRole('combobox', { name: /Untergrenze/ }), { target: { value: 'OFF' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/bank-accounts/1/lower-limit')
    expect(call?.[1]?.body).toBe(JSON.stringify({ mode: 'OFF', amount: null }))
  })

  it('creates a new account with its Kontoart', async () => {
    const fetchMock = renderPage()
    const card = await screen.findByRole('region', { name: 'Bankkonten' })
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
