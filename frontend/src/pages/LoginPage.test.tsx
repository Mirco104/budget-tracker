import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderLogin(answer: () => Promise<Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => answer())
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient()}>
      <LoginPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

function fillIn(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
}

describe('LoginPage', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  // US36
  it('explains how to get a new password', () => {
    renderLogin(() => json({}))

    expect(screen.queryByText(/Bitte deinen Admin/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Passwort vergessen?' }))

    expect(screen.getByText(/Bitte deinen Admin um einen Link für ein neues Passwort/)).toBeInTheDocument()
  })

  it('sends e-mail, password and the CSRF token from the cookie', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetchMock = renderLogin(() => Promise.resolve(new Response(null, { status: 204 })))

    fillIn(' person-a@example.test ', 'test-only-password')

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/login')
    expect(init?.method).toBe('POST')
    expect(init?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'token-123' })
    expect(JSON.parse(init?.body as string)).toEqual({
      email: 'person-a@example.test',
      password: 'test-only-password',
    })
  })

  it('shows the message of the backend and clears the password', async () => {
    renderLogin(() =>
      json({ status: 401, title: 'Anmeldung fehlgeschlagen', detail: 'E-Mail oder Passwort ist falsch.' }, 401),
    )

    fillIn('person-a@example.test', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('E-Mail oder Passwort ist falsch.')
    expect(screen.getByLabelText('Passwort')).toHaveValue('')
  })

  // US44: no button where the browser has no WebAuthn (jsdom)
  it('offers no passkey without WebAuthn in the browser', () => {
    renderLogin(() => json({}))

    expect(screen.queryByRole('button', { name: 'Mit Passkey anmelden' })).not.toBeInTheDocument()
  })

  it('signs in with a passkey: options, the dialog of the browser, then the answer as base64url', async () => {
    vi.stubGlobal('PublicKeyCredential', function PublicKeyCredential() {})
    const get = vi.fn(() =>
      Promise.resolve({
        id: 'AAE',
        rawId: new Uint8Array([0, 1]).buffer,
        type: 'public-key',
        authenticatorAttachment: 'platform',
        getClientExtensionResults: () => ({}),
        response: {
          authenticatorData: new Uint8Array([2]).buffer,
          clientDataJSON: new Uint8Array([3]).buffer,
          signature: new Uint8Array([4]).buffer,
          userHandle: new Uint8Array([5]).buffer,
        },
      }),
    )
    Object.defineProperty(navigator, 'credentials', { value: { get }, configurable: true })
    const fetchMock = renderLogin(() => Promise.resolve(new Response(null, { status: 204 })))
    fetchMock.mockImplementationOnce(() =>
      json({ challenge: 'AQID', rpId: 'localhost', userVerification: 'required', allowCredentials: [] }),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Mit Passkey anmelden' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/passkey/options')
    const options = (get.mock.calls[0] as unknown as [{ publicKey: PublicKeyCredentialRequestOptions }])[0].publicKey
    expect(new Uint8Array(options.challenge as ArrayBuffer)).toEqual(new Uint8Array([1, 2, 3]))
    expect(options.rpId).toBe('localhost')
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/passkey')
    expect(JSON.parse(init?.body as string)).toMatchObject({
      rawId: 'AAE',
      response: { authenticatorData: 'Ag', clientDataJSON: 'Aw', signature: 'BA', userHandle: 'BQ' },
    })
    Reflect.deleteProperty(navigator, 'credentials')
  })

  it('says so when the passkey dialog was cancelled', async () => {
    vi.stubGlobal('PublicKeyCredential', function PublicKeyCredential() {})
    const get = vi.fn(() => Promise.reject(new DOMException('cancelled', 'NotAllowedError')))
    Object.defineProperty(navigator, 'credentials', { value: { get }, configurable: true })
    renderLogin(() => json({ challenge: 'AQID', rpId: 'localhost' }))

    fireEvent.click(screen.getByRole('button', { name: 'Mit Passkey anmelden' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Der Vorgang wurde abgebrochen oder hat zu lange gedauert.',
    )
    Reflect.deleteProperty(navigator, 'credentials')
  })

  it('asks for both fields before sending', () => {
    const fetchMock = renderLogin(() => new Promise(() => {}))

    fillIn('', '')

    expect(screen.getByRole('alert')).toHaveTextContent('Bitte E-Mail und Passwort eingeben.')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
