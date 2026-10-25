import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Category } from '../api/types'
import { TransactionForm } from './TransactionForm'

const categories: Category[] = [
  { id: 1, name: 'Wohnen', group: 'FIXED', sortOrder: 1, targetAmount: null, targetPercent: null, targetValidFrom: null },
  { id: 20, name: 'Gehalt', group: null, sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null },
]

function renderForm(onSubmit = vi.fn(() => Promise.resolve())) {
  render(<TransactionForm categories={categories} transaction={null} defaultDate="2026-10-18" onSubmit={onSubmit} />)
  return onSubmit
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))
}

describe('TransactionForm', () => {
  it('shows required-field messages and does not submit an empty form', () => {
    const onSubmit = renderForm()
    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '' } })

    submit()

    expect(screen.getByText('Der Betrag ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Beschreibung ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Das Datum ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Kategorie ist Pflicht.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it.each(['0', '-5', '0,00'])('rejects a non-positive amount %s', (amount) => {
    const onSubmit = renderForm()
    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: amount } })

    submit()

    expect(screen.getByText('Der Betrag muss größer als 0 sein.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits an expense with a negative amount by default', async () => {
    const onSubmit = renderForm()
    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '12,50' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Miete' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '1' } })

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ amount: -12.5, description: 'Miete', date: '2026-10-18', categoryId: 1 }),
    )
  })

  it('submits income with a positive amount', async () => {
    const onSubmit = renderForm()
    fireEvent.click(screen.getByLabelText('Einnahme'))
    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '2000' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Gehalt' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '20' } })

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ amount: 2000, description: 'Gehalt', date: '2026-10-18', categoryId: 20 }),
    )
  })

  it('shows the amount of an expense positive with Ausgabe selected when editing', () => {
    render(
      <TransactionForm
        categories={categories}
        transaction={{ kind: 'SINGLE', id: 5, recurringId: null, occurrenceDate: null, amount: -45.99, description: 'Tanken', date: '2026-10-05', categoryId: 1, categoryName: 'Wohnen' }}
        defaultDate="2026-10-18"
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Betrag (€)')).toHaveValue('45,99')
    expect(screen.getByLabelText('Ausgabe')).toBeChecked()
    expect(screen.getByLabelText('Einnahme')).not.toBeChecked()
  })

  it('groups the categories like the settings', () => {
    renderForm()

    expect(screen.getByRole('group', { name: 'Fixkosten' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Ohne Gruppe' })).toBeInTheDocument()
  })

  it('shows field errors returned by the server', async () => {
    const onSubmit = vi.fn(() =>
      Promise.reject(new ApiError({ status: 400, errors: [{ field: 'categoryId', message: 'Kategorie nicht gefunden.' }] })),
    )
    renderForm(onSubmit)
    fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '5' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Test' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '20' } })

    submit()

    expect(await screen.findByText('Kategorie nicht gefunden.')).toBeInTheDocument()
  })
})
