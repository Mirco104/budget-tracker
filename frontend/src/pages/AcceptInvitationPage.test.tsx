import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AcceptInvitationPage } from './AcceptInvitationPage'

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(answer: () => Promise<Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => answer())
  vi.stubGlobal('fetch', fetchMock)
  const onAccepted = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AcceptInvitationPage token="token-abc" onAccepted={onAccepted} />
    </QueryClientProvider>,
  )
  return { fetchMock, onAccepted }
}

function fillIn(password: string, repeat: string) {
  fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: ' person-d@example.test ' } })
  fireEvent.change(screen.getByLabelText('Anzeigename'), { target: { value: 'Person D' } })
  fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: password } })
  fireEvent.change(screen.getByLabelText('Passwort wiederholen'), { target: { value: repeat } })
  fireEvent.click(screen.getByRole('button', { name: 'Konto anlegen' }))
}

describe('AcceptInvitationPage', () => {
  it('creates the account with the token from the link', async () => {
    const { fetchMock, onAccepted } = renderPage(() =>
      json({ id: 4, email: 'person-d@example.test', displayName: 'Person D', role: 'USER' }, 201),
    )

    fillIn('test-only-password', 'test-only-password')

    await waitFor(() => expect(onAccepted).toHaveBeenCalled())
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/invitations/token-abc/accept')
    expect(JSON.parse(init?.body as string)).toEqual({
      email: 'person-d@example.test',
      displayName: 'Person D',
      password: 'test-only-password',
    })
  })

  it('checks that both passwords match before sending', () => {
    const { fetchMock } = renderPage(() => new Promise(() => {}))

    fillIn('test-only-password', 'other-password')

    expect(screen.getByRole('alert')).toHaveTextContent('Die Passwörter stimmen nicht überein.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows the message for a used link', async () => {
    const { onAccepted } = renderPage(() =>
      json({ status: 410, title: 'Nicht mehr gültig', detail: 'Die Einladung ist abgelaufen oder wurde schon benutzt.' }, 410),
    )

    fillIn('test-only-password', 'test-only-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Die Einladung ist abgelaufen oder wurde schon benutzt.')
    expect(onAccepted).not.toHaveBeenCalled()
  })

  it('lets a user with an account sign in instead, ticket #133', async () => {
    const { fetchMock, onAccepted } = renderPage(() => Promise.resolve(new Response(null, { status: 204 })))

    fireEvent.click(screen.getByRole('button', { name: 'Schon ein Konto? Anmelden' }))
    expect(screen.getByRole('heading', { name: 'Anmelden' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: 'person-a@example.test' } })
    fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: 'test-only-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }))

    await waitFor(() => expect(onAccepted).toHaveBeenCalled())
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/login')
  })

  it('goes back from the sign-in to the invitation', () => {
    renderPage(() => new Promise(() => {}))

    fireEvent.click(screen.getByRole('button', { name: 'Schon ein Konto? Anmelden' }))
    fireEvent.click(screen.getByRole('button', { name: 'Zurück zur Einladung' }))

    expect(screen.getByRole('heading', { name: 'Einladung annehmen' })).toBeInTheDocument()
  })
})
