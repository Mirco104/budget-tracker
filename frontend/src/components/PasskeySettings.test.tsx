import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Passkey } from '../api/types'
import { PasskeySettings } from './PasskeySettings'

const passkeys: Passkey[] = [
  { id: 1, label: 'Laptop', createdAt: '2026-11-20T10:00:00Z', lastUsedAt: '2026-11-24T08:30:00Z' },
  { id: 2, label: 'Handy', createdAt: '2026-11-21T10:00:00Z', lastUsedAt: null },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

type Answer = (url: string, init?: RequestInit) => Promise<Response>

function renderSettings(onWrite: Answer, list: Passkey[] = passkeys) {
  const fetchMock = vi.fn<Answer>((url, init) =>
    init?.method && init.method !== 'GET' ? onWrite(url, init) : json(list),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient()}>
      <PasskeySettings />
    </QueryClientProvider>,
  )
  return fetchMock
}

function withWebAuthn(create: () => Promise<unknown>) {
  vi.stubGlobal('PublicKeyCredential', function PublicKeyCredential() {})
  Object.defineProperty(navigator, 'credentials', { value: { create }, configurable: true })
}

describe('PasskeySettings', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'credentials')
  })

  it('lists the passkeys with when they were added and used', async () => {
    renderSettings(() => json({}))

    expect(await screen.findByText('Laptop')).toBeInTheDocument()
    expect(screen.getByText(/zuletzt benutzt 24\.11\.2026/)).toBeInTheDocument()
    expect(screen.getByText(/hinzugefügt 21\.11\.2026.*noch nicht benutzt/)).toBeInTheDocument()
  })

  it('explains when the browser has no passkeys', async () => {
    renderSettings(() => json({}), [])

    expect(await screen.findByText('Du hast noch keinen Passkey.')).toBeInTheDocument()
    expect(screen.getByText('Dieser Browser unterstützt keine Passkeys.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Passkey hinzufügen' })).not.toBeInTheDocument()
  })

  it('adds a passkey with the password, the dialog of the browser and the name', async () => {
    const create = vi.fn(() =>
      Promise.resolve({
        id: 'BwgJ',
        rawId: new Uint8Array([7, 8, 9]).buffer,
        type: 'public-key',
        authenticatorAttachment: 'platform',
        getClientExtensionResults: () => ({ credProps: { rk: true } }),
        response: {
          attestationObject: new Uint8Array([1]).buffer,
          clientDataJSON: new Uint8Array([2]).buffer,
          getTransports: () => ['internal'],
        },
      }),
    )
    withWebAuthn(create)
    const fetchMock = renderSettings((url) =>
      url.endsWith('/options')
        ? json({
            challenge: 'AQID',
            rp: { id: 'localhost', name: 'Budget-Tracker' },
            user: { id: 'BAU', name: 'person-a@example.test', displayName: 'Person A' },
            pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
            excludeCredentials: [{ type: 'public-key', id: 'Bg' }],
          })
        : json({ id: 3, label: 'Tablet', createdAt: '2026-11-25T10:00:00Z', lastUsedAt: null }, 201),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Passkey hinzufügen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Bitte das Passwort eingeben.')

    fireEvent.change(screen.getByLabelText(/Name/), { target: { value: ' Tablet ' } })
    fireEvent.change(screen.getByLabelText('Passwort zur Bestätigung'), { target: { value: 'test-only-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Weiter' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Der Passkey „Tablet“ ist hinzugefügt.')
    const writes = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(writes[0][0]).toBe('/api/account/passkeys/options')
    expect(JSON.parse(writes[0][1]?.body as string)).toEqual({ password: 'test-only-password' })
    const options = (create.mock.calls[0] as unknown as [{ publicKey: PublicKeyCredentialCreationOptions }])[0]
      .publicKey
    expect(new Uint8Array(options.user.id as ArrayBuffer)).toEqual(new Uint8Array([4, 5]))
    expect(new Uint8Array(options.excludeCredentials?.[0].id as ArrayBuffer)).toEqual(new Uint8Array([6]))
    expect(writes[1][0]).toBe('/api/account/passkeys')
    expect(JSON.parse(writes[1][1]?.body as string)).toEqual({
      label: 'Tablet',
      credential: {
        id: 'BwgJ',
        rawId: 'BwgJ',
        type: 'public-key',
        response: { attestationObject: 'AQ', clientDataJSON: 'Ag', transports: ['internal'] },
        clientExtensionResults: { credProps: { rk: true } },
        authenticatorAttachment: 'platform',
      },
    })
  })

  it('shows the field error of a wrong password', async () => {
    withWebAuthn(() => Promise.reject(new Error('not called')))
    renderSettings(() =>
      json({ status: 400, errors: [{ field: 'password', message: 'Das Passwort ist falsch.' }] }, 400),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Passkey hinzufügen' }))
    fireEvent.change(screen.getByLabelText('Passwort zur Bestätigung'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: 'Weiter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Das Passwort ist falsch.')
  })

  it('renames and deletes a passkey', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = renderSettings((_url, init) =>
      init?.method === 'DELETE' ? Promise.resolve(new Response(null, { status: 204 })) : json(passkeys[0]),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Laptop umbenennen' }))
    fireEvent.change(screen.getByLabelText('Neuer Name'), { target: { value: 'Arbeitslaptop' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/account/passkeys/1', expect.objectContaining({ method: 'PUT' })),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Handy löschen' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/account/passkeys/2', expect.objectContaining({ method: 'DELETE' })),
    )
  })
})
