import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Loading, LoadingBar } from './Loading'

describe('Loading', () => {
  it('names what loads as a status', () => {
    render(<Loading text="Lade Übersicht …" />)

    expect(screen.getByRole('status')).toHaveTextContent('Lade Übersicht …')
  })
})

describe('LoadingBar', () => {
  function Slow({ answer }: { answer: Promise<string> }) {
    useQuery({ queryKey: ['slow'], queryFn: () => answer })
    return null
  }

  it('shows while a request runs and goes away once it is answered', async () => {
    let resolve: (value: string) => void = () => {}
    const answer = new Promise<string>((r) => (resolve = r))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <LoadingBar />
        <Slow answer={answer} />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('progressbar', { name: 'Lädt …' })).toBeInTheDocument()
    resolve('done')
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
  })

  it('shows nothing while nothing runs', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <LoadingBar />
      </QueryClientProvider>,
    )

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})
