import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the app title', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <App />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: 'Budget-Tracker' })).toBeInTheDocument()
  })
})
