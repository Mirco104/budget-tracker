import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BankAccount } from '../api/types'
import { BankAccountSettings } from './BankAccountSettings'

// Fictional accounts; the IBAN is the ISO 13616 documentation example, not a real account of anyone.
const accounts: BankAccount[] = [
  { id: 1, name: 'Girokonto', iban: 'DE89370400440532013000', defaultAccount: true, archived: false, used: true },
  { id: 2, name: 'Tagesgeld', iban: null, defaultAccount: false, archived: false, used: false },
  { id: 3, name: 'Depot', iban: null, defaultAccount: false, archived: false, used: true },
  { id: 4, name: 'Altes Konto', iban: null, defaultAccount: false, archived: true, used: true },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderSettings(answer: () => Promise<Response> = () => json({})) {
  const fetchMock = vi.fn((_url: string, init?: RequestInit) => (init?.method ? answer() : json(accounts)))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <BankAccountSettings />
    </QueryClientProvider>,
  )
  return fetchMock
}

function sent(fetchMock: ReturnType<typeof vi.fn>, method: string) {
  const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === method)
  return call ? { url: call[0] as string, body: JSON.parse(String((call[1] as RequestInit).body ?? 'null')) } : undefined
}

describe('BankAccountSettings', () => {
  it('lists the accounts with the default one marked and the IBAN in groups of four', async () => {
    renderSettings()

    const girokonto = await screen.findByRole('listitem', { name: 'Girokonto' })
    expect(within(girokonto).getByText('Standard')).toBeInTheDocument()
    expect(within(girokonto).getByText('DE89 3704 0044 0532 0130 00')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Archiviert (1)' })).toBeInTheDocument()
    expect(within(screen.getByRole('listitem', { name: 'Altes Konto' })).getByRole('button', { name: 'Wiederherstellen' }))
      .toBeInTheDocument()
  })

  it('creates an account with name and IBAN', async () => {
    const fetchMock = renderSettings()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto' }))

    const form = screen.getByRole('form', { name: 'Neues Konto' })
    fireEvent.change(within(form).getByLabelText('Name'), { target: { value: 'Tagesgeld 2' } })
    fireEvent.change(within(form).getByLabelText('IBAN (optional)'), { target: { value: 'de89 3704 0044 0532 0130 00' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(sent(fetchMock, 'POST')).toEqual({
        url: '/api/bank-accounts',
        body: { name: 'Tagesgeld 2', iban: 'de89 3704 0044 0532 0130 00' },
      }),
    )
  })

  it('requires a name before sending', async () => {
    const fetchMock = renderSettings()
    fireEvent.click(await screen.findByRole('button', { name: 'Konto' }))
    fireEvent.click(within(screen.getByRole('form', { name: 'Neues Konto' })).getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Der Name ist Pflicht.')
    expect(sent(fetchMock, 'POST')).toBeUndefined()
  })

  it('makes another account the default one from the menu', async () => {
    const fetchMock = renderSettings()
    const tagesgeld = await screen.findByRole('listitem', { name: 'Tagesgeld' })
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Aktionen für Tagesgeld' }))
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Als Standardkonto festlegen' }))

    await waitFor(() =>
      expect(sent(fetchMock, 'PUT')).toEqual({
        url: '/api/bank-accounts/2',
        body: { name: 'Tagesgeld', iban: null, archived: false, defaultAccount: true },
      }),
    )
  })

  it('offers no default, archive or delete for the default account', async () => {
    renderSettings()
    const girokonto = await screen.findByRole('listitem', { name: 'Girokonto' })
    fireEvent.click(within(girokonto).getByRole('button', { name: 'Aktionen für Girokonto' }))

    expect(within(girokonto).getByRole('button', { name: 'Bearbeiten' })).toBeInTheDocument()
    expect(within(girokonto).queryByRole('button', { name: 'Archivieren' })).not.toBeInTheDocument()
    expect(within(girokonto).queryByRole('button', { name: 'Löschen' })).not.toBeInTheDocument()
  })

  it('deletes only an unused account, after asking once more', async () => {
    const fetchMock = renderSettings()
    const depot = await screen.findByRole('listitem', { name: 'Depot' })
    fireEvent.click(within(depot).getByRole('button', { name: 'Aktionen für Depot' }))
    expect(within(depot).getByRole('button', { name: 'Löschen' })).toBeDisabled()

    const tagesgeld = screen.getByRole('listitem', { name: 'Tagesgeld' })
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Aktionen für Tagesgeld' }))
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Löschen' }))
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Endgültig löschen' }))

    await waitFor(() => expect(sent(fetchMock, 'DELETE')?.url).toBe('/api/bank-accounts/2'))
  })

  it('edits name and IBAN and shows the error of the backend', async () => {
    const fetchMock = renderSettings(() =>
      json({ status: 400, errors: [{ field: 'iban', message: 'Die IBAN ist ungültig.' }] }, 400),
    )
    const tagesgeld = await screen.findByRole('listitem', { name: 'Tagesgeld' })
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Aktionen für Tagesgeld' }))
    fireEvent.click(within(tagesgeld).getByRole('button', { name: 'Bearbeiten' }))
    const form = screen.getByRole('form', { name: 'Tagesgeld bearbeiten' })
    fireEvent.change(within(form).getByLabelText('IBAN (optional)'), { target: { value: 'DE00' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Die IBAN ist ungültig.')
    expect(sent(fetchMock, 'PUT')?.body).toEqual({ name: 'Tagesgeld', iban: 'DE00', archived: false, defaultAccount: false })
  })
})
