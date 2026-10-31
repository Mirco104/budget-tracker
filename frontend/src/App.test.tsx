import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  it('has the tabs Übersicht, Transaktionen, Planung, Auswertung and Einstellungen', () => {
    renderApp()

    expect(screen.getByRole('button', { name: 'Übersicht' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Transaktionen' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Wiederkehrend' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Einstellungen' })).toBeInTheDocument()
  })

  it('keeps the month selection on the planning page', () => {
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Planung' }))

    expect(screen.getByLabelText('Monat')).toBeInTheDocument()
  })
  // The analysis page is loaded on first use, because of the chart library
  it('loads the analysis page when its tab is opened', async () => {
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Auswertung' }))

    expect(screen.getByText('Lade Auswertung …')).toBeInTheDocument()
    // Transforming Recharts for the first import takes a while in the test environment
    await waitFor(
      () => expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/reserves\?month=.*&months=12$/), expect.anything()),
      { timeout: 5000 },
    )
  })

  // US10: settings do not depend on a month
  it('hides the month selection on the settings page', () => {
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: 'Einstellungen' }))

    expect(screen.getByRole('heading', { name: 'Einstellungen' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Monat')).not.toBeInTheDocument()
  })
})
