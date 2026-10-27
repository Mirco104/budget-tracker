import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Category } from '../api/types'
import { TransactionForm, type RepeatOption, type TransactionFormValues } from './TransactionForm'

const categories: Category[] = [
  { id: 1, name: 'Wohnen', sortOrder: 1, targetAmount: null, targetPercent: null, targetValidFrom: null },
  { id: 20, name: 'Gehalt', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null },
]

function renderForm(
  onSubmit = vi.fn(() => Promise.resolve()),
  initial: TransactionFormValues | null = null,
  repeatOption: RepeatOption = 'optional',
) {
  render(
    <TransactionForm
      categories={categories}
      initial={initial}
      defaultDate="2026-10-18"
      repeatOption={repeatOption}
      title="Neue Transaktion"
      submitLabel="Hinzufügen"
      onSubmit={onSubmit}
    />,
  )
  return onSubmit
}

function fillValid() {
  fireEvent.change(screen.getByLabelText('Betrag (€)'), { target: { value: '240' } })
  fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Haftpflicht' } })
  fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '1' } })
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
      expect(onSubmit).toHaveBeenCalledWith({
        kind: 'single',
        input: { amount: -12.5, description: 'Miete', date: '2026-10-18', categoryId: 1 },
      }),
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
      expect(onSubmit).toHaveBeenCalledWith({
        kind: 'single',
        input: { amount: 2000, description: 'Gehalt', date: '2026-10-18', categoryId: 20 },
      }),
    )
  })

  it('shows the amount of an expense positive with Ausgabe selected when editing', () => {
    renderForm(
      vi.fn(),
      { amount: -45.99, description: 'Tanken', date: '2026-10-05', categoryId: 1, repeat: 'NONE', endDate: null },
      'none',
    )

    expect(screen.getByLabelText('Betrag (€)')).toHaveValue('45,99')
    expect(screen.getByLabelText('Ausgabe')).toBeChecked()
    expect(screen.getByLabelText('Einnahme')).not.toBeChecked()
    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
  })

  // --- Wiederholen (US6) ---

  it('creates a single transaction unless a repeat is chosen', () => {
    renderForm()

    expect(screen.getByLabelText('Wiederholen')).toHaveValue('NONE')
    expect(screen.queryByLabelText('Enddatum (optional)')).not.toBeInTheDocument()
  })

  it('submits a series when a repeat is chosen', async () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'YEARLY' } })
    fireEvent.change(screen.getByLabelText('Datum (erste Abbuchung)'), { target: { value: '2026-10-15' } })

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        kind: 'recurring',
        input: {
          amount: -240,
          description: 'Haftpflicht',
          categoryId: 1,
          interval: 'YEARLY',
          startDate: '2026-10-15',
          endDate: null,
        },
      }),
    )
  })

  it('submits the end date of a series', async () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    fireEvent.change(screen.getByLabelText('Enddatum (optional)'), { target: { value: '2027-03-31' } })

    submit()

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'recurring', input: expect.objectContaining({ endDate: '2027-03-31' }) }),
      ),
    )
  })

  it('rejects an end date before the first occurrence', () => {
    const onSubmit = renderForm()
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    fireEvent.change(screen.getByLabelText('Enddatum (optional)'), { target: { value: '2026-09-30' } })

    submit()

    expect(screen.getByText('Das Enddatum darf nicht vor dem Startdatum liegen.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('offers only intervals when editing a series', () => {
    renderForm(
      vi.fn(),
      { amount: -15, description: 'Streaming', date: '2026-11-05', categoryId: 1, repeat: 'QUARTERLY', endDate: '2027-12-31' },
      'required',
    )

    expect(screen.getByLabelText('Wiederholen')).toHaveValue('QUARTERLY')
    expect(screen.queryByRole('option', { name: 'nie' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Datum (erste Abbuchung)')).toHaveValue('2026-11-05')
    expect(screen.getByLabelText('Enddatum (optional)')).toHaveValue('2027-12-31')
    expect(screen.getByLabelText('Betrag (€)')).toHaveValue('15,00')
  })

  it('shows a server error on startDate at the date field', async () => {
    const onSubmit = vi.fn(() =>
      Promise.reject(new ApiError({ status: 400, errors: [{ field: 'startDate', message: 'Startdatum zu früh.' }] })),
    )
    renderForm(onSubmit)
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })

    submit()

    expect(await screen.findByText('Startdatum zu früh.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Datum \(erste Abbuchung\)/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('lists the categories without groups', () => {
    renderForm()

    const select = screen.getByLabelText('Kategorie')
    expect(within(select).queryByRole('group')).not.toBeInTheDocument()
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Bitte wählen',
      'Wohnen',
      'Gehalt',
    ])
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
