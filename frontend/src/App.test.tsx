import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp() {
  vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
  render(
    <QueryClientProvider client={new QueryClient()}>
      <App />
    </QueryClientProvider>,
  )
}

describe('App', () => {
  it('renders the app title', () => {
    renderApp()

    expect(screen.getByRole('heading', { name: 'Budget-Tracker' })).toBeInTheDocument()
  })

  it('moves the selected month with the previous and next buttons', () => {
    renderApp()
    const monthInput = screen.getByLabelText('Monat')
    fireEvent.change(monthInput, { target: { value: '2026-12' } })

    fireEvent.click(screen.getByRole('button', { name: 'Nächster Monat' }))
    expect(monthInput).toHaveValue('2027-01')

    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    fireEvent.click(screen.getByRole('button', { name: 'Vorheriger Monat' }))
    expect(monthInput).toHaveValue('2026-11')
  })

  it('jumps back to the current month', () => {
    renderApp()
    const monthInput = screen.getByLabelText('Monat')
    const today = new Date()
    const current = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
    const currentButton = screen.getByRole('button', { name: 'Aktueller Monat' })
    expect(currentButton).toBeDisabled()

    fireEvent.change(monthInput, { target: { value: '2020-05' } })
    expect(currentButton).toBeEnabled()
    fireEvent.click(currentButton)

    expect(monthInput).toHaveValue(current)
  })

  it('keeps the month selection on the settings page', () => {
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Einstellungen' }))

    expect(screen.getByLabelText('Monat')).toBeInTheDocument()
  })
})
