import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ModeBadge } from './ModeBadge'

function renderBadge(health: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.resolve(new Response(JSON.stringify(health), { status: 200 }))),
  )
  document.title = 'Budget-Tracker'
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ModeBadge />
    </QueryClientProvider>,
  )
}

describe('ModeBadge', () => {
  it('names a local mode and puts it in front of the tab title', async () => {
    const { unmount } = renderBadge({ status: 'UP', mode: 'sample' })

    expect(await screen.findByText('Beispieldaten (sample)')).toBeInTheDocument()
    expect(document.title).toBe('[sample] Budget-Tracker')
    unmount()
    expect(document.title).toBe('Budget-Tracker')
  })

  it('names the default mode dev', async () => {
    renderBadge({ status: 'UP', mode: 'dev' })

    expect(await screen.findByText('Entwicklung (dev)')).toBeInTheDocument()
  })

  it('shows nothing in production', async () => {
    const { container } = renderBadge({ status: 'UP', mode: null })

    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/health', expect.anything()))
    expect(container).toBeEmptyDOMElement()
    expect(document.title).toBe('Budget-Tracker')
  })
})
