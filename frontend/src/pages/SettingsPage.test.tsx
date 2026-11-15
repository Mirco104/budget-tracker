import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { SettingsPage } from './SettingsPage'

const user: Me = { id: 2, email: 'person-b@example.test', displayName: 'Person B', role: 'USER' }
const admin: Me = { id: 1, email: 'person-a@example.test', displayName: 'Person A', role: 'ADMIN' }

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

// Categories load as an empty list; writing calls get the answer of the test. Most tests are about the account.
function renderSettings(
  me: Me,
  onWrite: (url: string, init: RequestInit) => Promise<Response> = () => json({}),
  hash = '#/einstellungen/konto',
) {
  window.location.hash = hash
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

function subPages() {
  return within(screen.getByRole('navigation', { name: 'Einstellungen' }))
    .getAllByRole('link')
    .map((link) => [link.textContent, link.getAttribute('href')])
}

describe('SettingsPage', () => {
  afterEach(() => {
    window.location.hash = ''
  })

  // US10: sub pages with their own address (US29); invitations and users only for admins
  it('has the sub pages Kategorien and Konto, for admins also Einladungen and Nutzer (Bankkonten moved to Konten)', () => {
    renderSettings(user)
    expect(subPages()).toEqual([
      ['Kategorien', '#/einstellungen/kategorien'],
      ['Konto', '#/einstellungen/konto'],
    ])
    expect(screen.getByRole('link', { name: 'Konto' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { name: 'Konto' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Kategorien' })).not.toBeInTheDocument()
    cleanup()

    renderSettings({ ...admin, role: 'SYSTEM_ADMIN' }, undefined, '#/einstellungen/nutzer')
    expect(subPages()).toEqual([
      ['Kategorien', '#/einstellungen/kategorien'],
      ['Konto', '#/einstellungen/konto'],
      ['Einladungen', '#/einstellungen/einladungen'],
      ['Nutzer', '#/einstellungen/nutzer'],
    ])
    expect(screen.getByRole('heading', { name: 'Nutzer' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Konto' })).not.toBeInTheDocument()
  })

  it('shows the categories without a sub page in the address', async () => {
    renderSettings(user, undefined, '#/einstellungen')

    expect(screen.getByRole('link', { name: 'Kategorien' })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByRole('heading', { name: 'Kategorien' })).toBeInTheDocument()
  })

  it('shows the categories instead of an admin sub page to a user', async () => {
    renderSettings(user, undefined, '#/einstellungen/nutzer')

    expect(await screen.findByRole('heading', { name: 'Kategorien' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Nutzer' })).not.toBeInTheDocument()
  })

  it('shows name and e-mail and opens a form only after Ändern', () => {
    renderSettings(user)

    expect(screen.getByText('Person B')).toBeInTheDocument()
    expect(screen.getByText('person-b@example.test')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'E-Mail ändern' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'E-Mail ändern' }))
    const form = screen.getByRole('form', { name: 'E-Mail ändern' })
    expect(within(form).getByLabelText('Neue E-Mail-Adresse')).toHaveValue('person-b@example.test')
    expect(screen.queryByRole('button', { name: 'E-Mail ändern' })).not.toBeInTheDocument()

    // Opening another form closes this one
    fireEvent.click(screen.getByRole('button', { name: 'Passwort ändern' }))
    expect(screen.queryByRole('form', { name: 'E-Mail ändern' })).not.toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Passwort ändern' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(screen.queryByRole('form', { name: 'Passwort ändern' })).not.toBeInTheDocument()
  })

  it('has no e-mail button for the system admin', () => {
    renderSettings({ ...admin, role: 'SYSTEM_ADMIN' })

    expect(screen.queryByRole('button', { name: 'E-Mail ändern' })).not.toBeInTheDocument()
    expect(screen.getByText('Als Systemadmin über ADMIN_EMAIL festgelegt.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anzeigename ändern' })).toBeInTheDocument()
  })

  it('changes the display name and shows the message after closing the form', async () => {
    const fetchMock = renderSettings(user, () => json({ ...user, displayName: 'Person B neu' }))

    fireEvent.click(screen.getByRole('button', { name: 'Anzeigename ändern' }))
    const form = screen.getByRole('form', { name: 'Anzeigename ändern' })
    fireEvent.change(within(form).getByLabelText('Neuer Anzeigename'), { target: { value: ' Person B neu ' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Der Anzeigename ist geändert.')
    expect(screen.queryByRole('form', { name: 'Anzeigename ändern' })).not.toBeInTheDocument()
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account/display-name')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ displayName: 'Person B neu' })
  })

  it('changes the e-mail with the password and shows a taken address', async () => {
    const fetchMock = renderSettings(user, () =>
      json({ status: 409, detail: 'Diese E-Mail-Adresse ist schon registriert.' }, 409),
    )
    fireEvent.click(screen.getByRole('button', { name: 'E-Mail ändern' }))
    const form = screen.getByRole('form', { name: 'E-Mail ändern' })

    fireEvent.change(within(form).getByLabelText('Neue E-Mail-Adresse'), { target: { value: ' admin@example.test ' } })
    fireEvent.change(within(form).getByLabelText('Passwort zur Bestätigung'), { target: { value: 'test-only-password' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Diese E-Mail-Adresse ist schon registriert.')
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account/email')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ email: 'admin@example.test', password: 'test-only-password' })
  })

  it('creates an invitation link for an admin', async () => {
    renderSettings(
      admin,
      () => json({ link: 'http://localhost:5173/?einladung=token-abc', expiresAt: '2026-11-08T17:00:00Z' }, 201),
      '#/einstellungen/einladungen',
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
    fireEvent.click(screen.getByRole('button', { name: 'Passwort ändern' }))
    const form = screen.getByRole('form', { name: 'Passwort ändern' })

    fireEvent.change(within(form).getByLabelText('Bisheriges Passwort'), { target: { value: 'wrong-password' } })
    fireEvent.change(within(form).getByLabelText(/Neues Passwort \(/), { target: { value: 'new-test-password' } })
    fireEvent.change(within(form).getByLabelText('Neues Passwort wiederholen'), { target: { value: 'new-test-password' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Das Passwort ist falsch.')
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/account/password')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({
      currentPassword: 'wrong-password',
      newPassword: 'new-test-password',
    })
  })

  // US16: the new password is typed twice, so a typo cannot lock the user out
  it('sends no new password until both entries match', async () => {
    const fetchMock = renderSettings(user)
    fireEvent.click(screen.getByRole('button', { name: 'Passwort ändern' }))
    const form = screen.getByRole('form', { name: 'Passwort ändern' })
    fireEvent.change(within(form).getByLabelText('Bisheriges Passwort'), { target: { value: 'old-test-password' } })
    fireEvent.change(within(form).getByLabelText(/Neues Passwort \(/), { target: { value: 'new-test-password' } })

    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))
    expect(within(form).getByRole('alert')).toHaveTextContent('Bitte alle drei Felder ausfüllen.')

    fireEvent.change(within(form).getByLabelText('Neues Passwort wiederholen'), { target: { value: 'new-test-pasword' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))
    expect(within(form).getByRole('alert')).toHaveTextContent('Die neuen Passwörter stimmen nicht überein.')
    expect(writeCalls(fetchMock)).toHaveLength(0)

    fireEvent.change(within(form).getByLabelText('Neues Passwort wiederholen'), { target: { value: 'new-test-password' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(JSON.parse(writeCalls(fetchMock)[0][1]?.body as string)).toEqual({
      currentPassword: 'old-test-password',
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
