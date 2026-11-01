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

  it('asks for both fields before sending', () => {
    const fetchMock = renderLogin(() => new Promise(() => {}))

    fillIn('', '')

    expect(screen.getByRole('alert')).toHaveTextContent('Bitte E-Mail und Passwort eingeben.')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
