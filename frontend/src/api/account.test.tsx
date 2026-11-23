import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useChangeSetup } from './account'
import { useMe } from './auth'

afterEach(() => {
  vi.unstubAllGlobals()
})

// Field test after 0.3.0: "Einrichtung abschließen" led back to the start of the setup on the phone
describe('useChangeSetup', () => {
  it('keeps the new user when an older reload of the user answers later', async () => {
    let answerOldReload: (response: Response) => void = () => {}
    let meCalls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url === '/api/auth/me') {
          meCalls += 1
          const body = JSON.stringify({ email: 'a@example.test', setupDone: false })
          if (meCalls === 1) {
            return Promise.resolve(new Response(body, { status: 200 }))
          }
          // The reload on returning to the tab is still on its way while the setup is saved
          return new Promise<Response>((resolve) => {
            answerOldReload = resolve
          })
        }
        return Promise.resolve(new Response(JSON.stringify({ email: 'a@example.test', setupDone: true }), { status: 200 }))
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => ({ me: useMe(), setup: useChangeSetup() }), { wrapper })
    await waitFor(() => expect(result.current.me.data).toMatchObject({ setupDone: false }))

    void client.refetchQueries({ queryKey: ['me'] })
    await act(() => result.current.setup.mutateAsync(true))
    answerOldReload(new Response(JSON.stringify({ email: 'a@example.test', setupDone: false }), { status: 200 }))
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(client.getQueryData(['me'])).toMatchObject({ setupDone: true })
  })
})
