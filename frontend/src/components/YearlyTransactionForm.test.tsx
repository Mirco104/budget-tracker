import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Category } from '../api/types'
import { YearlyTransactionForm } from './YearlyTransactionForm'

const categories: Category[] = [
  { id: 3, name: 'Versicherungen', type: 'EXPENSE', group: 'FIXED', sortOrder: 3, targetAmount: null, targetPercent: null, targetValidFrom: null, system: false },
  { id: 20, name: 'Gehalt', type: 'INCOME', group: null, sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, system: false },
]

function renderForm(onSubmit = vi.fn(() => Promise.resolve())) {
  render(
    <YearlyTransactionForm categories={categories} transaction={null} defaultValidFrom="2026-10" onSubmit={onSubmit} />,
  )
  return onSubmit
}

function fillRequired() {
  fireEvent.change(screen.getByLabelText('Betrag pro Jahr (€)'), { target: { value: '600,00' } })
  fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Kfz-Versicherung' } })
  fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '3' } })
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))
}

describe('YearlyTransactionForm', () => {
  it('shows required-field messages and does not submit an empty form', () => {
    const onSubmit = renderForm()
    fireEvent.change(screen.getByLabelText('Gültig ab'), { target: { value: '' } })

    submit()

    expect(screen.getByText('Der Betrag ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Beschreibung ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Der Monat „gültig ab“ ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Kategorie ist Pflicht.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects an end month before the start month', () => {
    const onSubmit = renderForm()
    fillRequired()
    fireEvent.change(screen.getByLabelText('Gültig bis (optional)'), { target: { value: '2026-09' } })

    submit()

    expect(screen.getByText('Der Monat „gültig bis“ darf nicht vor „gültig ab“ liegen.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits an open-ended entry starting in the preset month', async () => {
    const onSubmit = renderForm()
    fillRequired()

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        amountPerYear: 600,
        description: 'Kfz-Versicherung',
        dueDate: null,
        validFrom: '2026-10',
        validTo: null,
        categoryId: 3,
      }),
    )
  })

  it('shows field errors returned by the server', async () => {
    const onSubmit = vi.fn(() =>
      Promise.reject(new ApiError({ status: 400, errors: [{ field: 'validTo', message: 'Ende ungültig.' }] })),
    )
    renderForm(onSubmit)
    fillRequired()

    submit()

    expect(await screen.findByText('Ende ungültig.')).toBeInTheDocument()
  })
})
