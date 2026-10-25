import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category, RecurringTransactionInput } from '../api/types'
import { RecurringTransactionForm } from './RecurringTransactionForm'

const categories: Category[] = [
  { id: 3, name: 'Versicherungen', group: 'FIXED', sortOrder: 3, targetAmount: null, targetPercent: null, targetValidFrom: null, system: false },
  { id: 15, name: 'Sonstiges (berechneter Rest)', group: 'REMAINDER', sortOrder: 15, targetAmount: null, targetPercent: null, targetValidFrom: null, system: true },
  { id: 20, name: 'Gehalt', group: null, sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, system: false },
]

function renderForm(initial: RecurringTransactionInput | null = null, onSubmit = vi.fn(() => Promise.resolve())) {
  render(
    <RecurringTransactionForm
      categories={categories}
      initial={initial}
      defaultStartDate="2026-10-01"
      title="Neue wiederkehrende Transaktion"
      submitLabel="Hinzufügen"
      onSubmit={onSubmit}
    />,
  )
  return onSubmit
}

function fillValid() {
  fireEvent.change(screen.getByLabelText('Betrag je Vorkommen (€)'), { target: { value: '240' } })
  fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Haftpflicht' } })
  fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '3' } })
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))
}

describe('RecurringTransactionForm', () => {
  it('submits an expense series with a negative amount and no end date', async () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Intervall'), { target: { value: 'YEARLY' } })
    fireEvent.change(screen.getByLabelText('Startdatum (erste Abbuchung)'), { target: { value: '2026-10-15' } })

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        amount: -240,
        description: 'Haftpflicht',
        categoryId: 3,
        interval: 'YEARLY',
        startDate: '2026-10-15',
        endDate: null,
      }),
    )
  })

  it('submits an income series with a positive amount', async () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.click(screen.getByLabelText('Einnahme'))

    submit()

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ amount: 240 })))
  })

  it.each(['0', '-5'])('rejects a non-positive amount %s', (amount) => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Betrag je Vorkommen (€)'), { target: { value: amount } })

    submit()

    expect(screen.getByText('Der Betrag muss größer als 0 sein.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects an end date before the start date', () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Enddatum (optional)'), { target: { value: '2026-09-30' } })

    submit()

    expect(screen.getByText('Das Enddatum darf nicht vor dem Startdatum liegen.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows the initial values with the amount positive', () => {
    renderForm({
      amount: -15,
      description: 'Streaming',
      categoryId: 3,
      interval: 'QUARTERLY',
      startDate: '2026-11-05',
      endDate: '2027-12-31',
    })

    expect(screen.getByLabelText('Betrag je Vorkommen (€)')).toHaveValue('15,00')
    expect(screen.getByLabelText('Ausgabe')).toBeChecked()
    expect(screen.getByLabelText('Intervall')).toHaveValue('QUARTERLY')
    expect(screen.getByLabelText('Startdatum (erste Abbuchung)')).toHaveValue('2026-11-05')
    expect(screen.getByLabelText('Enddatum (optional)')).toHaveValue('2027-12-31')
  })

  it('does not offer the remainder category', () => {
    renderForm()

    expect(screen.queryByRole('option', { name: 'Sonstiges (berechneter Rest)' })).not.toBeInTheDocument()
  })
})
