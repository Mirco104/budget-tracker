import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { SettingsPage } from './SettingsPage'

const user: Me = { id: 2, email: 'person-b@example.test', displayName: 'Person B', role: 'USER' }
const admin: Me = { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'ADMIN' }

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

// Categories load as an empty list; writing calls get the answer of the test
function renderSettings(me: Me, onWrite: (url: string, init: RequestInit) => Promise<Response> = () => json({})) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) =>
    init?.method && init.method !== 'GET' ? onWrite(url, init) : json([]),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SettingsPage me={me} />
    </QueryClientProvider>,
  )
  return fetchMock
}

function writeCalls(fetchMock: ReturnType<typeof renderSettings>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

describe('SettingsPage', () => {
  it('shows users and invitations to admins and the system admin, not to users', () => {
    renderSettings(user)
    expect(screen.getByRole('heading', { name: 'Konto' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Einladungen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Nutzer' })).not.toBeInTheDocument()
    cleanup()

    renderSettings({ ...admin, role: 'SYSTEM_ADMIN' })
    expect(screen.getByRole('heading', { name: 'Einladungen' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Nutzer' })).toBeInTheDocument()
  })

  it('changes the e-mail with the password and shows the current one', async () => {
    const fetchMock = renderSettings(user, () =>
      json({ status: 409, detail: 'Diese E-Mail-Adresse ist schon registriert.' }, 409),
    )
    const form = screen.getByRole('form', { name: 'E-Mail ändern' })
    expect(within(form).getByText('Aktuell: person-b@example.test')).toBeInTheDocument()

    fireEvent.change(within(form).getByLabelText('Neue E-Mail-Adresse'), { target: { value: ' admin@example.test ' } })
    fireEvent.change(within(form).getByLabelText('Passwort'), { target: { value: 'test-only-password' } })
    fireEvent.click(within(form).getByRole('button', { name: 'E-Mail ändern' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Diese E-Mail-Adresse ist schon registriert.')
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account/email')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ email: 'admin@example.test', password: 'test-only-password' })
  })

  it('creates an invitation link for an admin', async () => {
    renderSettings(admin, () =>
      json({ link: 'http://localhost:5173/?einladung=token-abc', expiresAt: '2026-11-08T17:00:00Z' }, 201),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Einladungslink erzeugen' }))

    expect(await screen.findByLabelText('Einladungslink')).toHaveValue('http://localhost:5173/?einladung=token-abc')
    expect(screen.getByText(/Gültig bis 08\.11\.2026/)).toBeInTheDocument()

    const mailto = screen.getByRole('link', { name: 'Per E-Mail senden' }).getAttribute('href') ?? ''
    expect(mailto).toMatch(/^mailto:\?subject=Einladung%20zum%20Budget-Tracker&body=/)
    const body = decodeURIComponent(mailto.slice(mailto.indexOf('body=') + 'body='.length))
    expect(body).toContain('http://localhost:5173/?einladung=token-abc')
    expect(body).toContain('gültig bis 08.11.2026')
    expect(body).toMatch(/Person A$/)
  })

  it('changes the password and shows a wrong current password', async () => {
    const fetchMock = renderSettings(user, () =>
      json({ status: 400, errors: [{ field: 'currentPassword', message: 'Das Passwort ist falsch.' }] }, 400),
    )
    const form = screen.getByRole('form', { name: 'Passwort ändern' })

    fireEvent.change(within(form).getByLabelText('Bisheriges Passwort'), { target: { value: 'wrong-password' } })
    fireEvent.change(within(form).getByLabelText(/Neues Passwort/), { target: { value: 'new-test-password' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Passwort ändern' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Das Passwort ist falsch.')
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account/password')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({
      currentPassword: 'wrong-password',
      newPassword: 'new-test-password',
    })
  })

  it('deletes the account only after the confirmation, with the password', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const fetchMock = renderSettings(user, () => Promise.resolve(new Response(null, { status: 204 })))
    const form = screen.getByRole('form', { name: 'Konto löschen' })
    fireEvent.change(within(form).getByLabelText('Passwort'), { target: { value: 'test-only-password' } })

    fireEvent.click(within(form).getByRole('button', { name: 'Konto löschen' }))
    expect(writeCalls(fetchMock)).toHaveLength(0)

    fireEvent.click(within(form).getByRole('button', { name: 'Konto löschen' }))
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account')
    expect(init?.method).toBe('DELETE')
    expect(JSON.parse(init?.body as string)).toEqual({ password: 'test-only-password' })
    expect(confirm).toHaveBeenCalledTimes(2)
  })
})
