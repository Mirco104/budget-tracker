import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ResetPasswordPage } from './ResetPasswordPage'

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(answer: () => Promise<Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => answer())
  vi.stubGlobal('fetch', fetchMock)
  const onDone = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ResetPasswordPage token="token-abc" onDone={onDone} />
    </QueryClientProvider>,
  )
  return { fetchMock, onDone }
}

function fillIn(password: string, repeat: string) {
  fireEvent.change(screen.getByLabelText('Neues Passwort'), { target: { value: password } })
  fireEvent.change(screen.getByLabelText('Passwort wiederholen'), { target: { value: repeat } })
  fireEvent.click(screen.getByRole('button', { name: 'Passwort speichern' }))
}

// US36
describe('ResetPasswordPage', () => {
  it('sets the new password with the token and signs in', async () => {
    const me = { id: 3, email: 'person-c@example.test', displayName: 'Person C', role: 'USER' }
    const { fetchMock, onDone } = renderPage(() => json(me))

    fillIn('new-test-only-password', 'new-test-only-password')

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/password-resets/token-abc')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ password: 'new-test-only-password' })
  })

  it('asks for the same password twice and sends nothing otherwise', () => {
    const { fetchMock } = renderPage(() => json({}))

    fillIn('new-test-only-password', 'other-password-123')

    expect(screen.getByRole('alert')).toHaveTextContent('Die Passwörter stimmen nicht überein.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows why the link does not work any more', async () => {
    const { onDone } = renderPage(() =>
      json({ status: 410, title: 'Nicht mehr gültig', detail: 'Der Link ist abgelaufen oder wurde schon benutzt.' }, 410),
    )

    fillIn('new-test-only-password', 'new-test-only-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Der Link ist abgelaufen oder wurde schon benutzt.')
    expect(onDone).not.toHaveBeenCalled()
  })
})
