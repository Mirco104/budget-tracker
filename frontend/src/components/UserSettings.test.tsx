import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Me, UserSummary } from '../api/types'
import { UserSettings } from './UserSettings'

// Fictional accounts, shaped like the backend response
const users: UserSummary[] = [
  { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'SYSTEM_ADMIN', createdAt: '2026-11-01T10:00:00Z', lastLoginAt: '2026-11-01T18:30:00Z' },
  { id: 2, email: 'person-b@example.test', displayName: 'Person B', role: 'ADMIN', createdAt: '2026-11-01T11:00:00Z', lastLoginAt: '2026-11-01T12:00:00Z' },
  { id: 3, email: 'person-c@example.test', displayName: 'Person C', role: 'USER', createdAt: '2026-11-01T12:00:00Z', lastLoginAt: null },
  { id: 4, email: 'person-d@example.test', displayName: 'Person D', role: 'ADMIN', createdAt: '2026-11-01T13:00:00Z', lastLoginAt: null },
]
const me: Me = { id: 2, email: 'person-b@example.test', displayName: 'Person B', role: 'ADMIN' }

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderUsers(onWrite: (url: string, init: RequestInit) => Promise<Response> = () => json(users[2])) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) =>
    init?.method && init.method !== 'GET' ? onWrite(url, init) : json(users),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient()}>
      <UserSettings me={me} />
    </QueryClientProvider>,
  )
  return fetchMock
}

describe('UserSettings', () => {
  it('lists the accounts with role, e-mail and last sign-in', async () => {
    renderUsers()

    const c = await screen.findByLabelText('Person C')
    expect(within(c).getByText('person-c@example.test')).toBeInTheDocument()
    expect(within(c).getByText(/User · seit .* · letzte Anmeldung noch nie/)).toBeInTheDocument()
    expect(within(screen.getByLabelText('Person A')).getByText(/Systemadmin/)).toBeInTheDocument()
    expect(within(screen.getByLabelText('Person A')).getByText(/letzte Anmeldung 01\.11\.2026/)).toBeInTheDocument()
  })

  it('shows the own name with (Du) and no button for oneself or the system admin', async () => {
    renderUsers()

    const own = await screen.findByLabelText('Person B')
    expect(within(own).getByText('Person B (Du)')).toBeInTheDocument()
    expect(within(own).queryByRole('button')).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Person A')).queryByRole('button')).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Person C')).getByRole('button', { name: 'Zum Admin machen' })).toBeInTheDocument()
    expect(within(screen.getByLabelText('Person D')).getByRole('button', { name: 'Zum User machen' })).toBeInTheDocument()
  })

  it('makes a user an admin', async () => {
    const fetchMock = renderUsers()

    fireEvent.click(within(await screen.findByLabelText('Person C')).getByRole('button', { name: 'Zum Admin machen' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/users/3/role', expect.objectContaining({ method: 'PUT' })),
    )
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/users/3/role')!
    expect(JSON.parse(init?.body as string)).toEqual({ role: 'ADMIN' })
  })

  it('shows the error of the backend in the row', async () => {
    renderUsers(() => json({ status: 409, detail: 'Der Systemadmin bleibt immer Systemadmin.' }, 409))

    const d = await screen.findByLabelText('Person D')
    fireEvent.click(within(d).getByRole('button', { name: 'Zum User machen' }))

    expect(await within(d).findByText('Der Systemadmin bleibt immer Systemadmin.')).toBeInTheDocument()
  })
})
