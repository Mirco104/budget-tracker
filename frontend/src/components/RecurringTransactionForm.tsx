import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, RecurringInterval, RecurringTransactionInput } from '../api/types'
import { parseAmountInput } from '../format'
import { INTERVAL_LABELS } from '../labels'
import { CategorySelect } from './CategorySelect'

type Field = 'amount' | 'description' | 'categoryId' | 'interval' | 'startDate' | 'endDate'
type Errors = Partial<Record<Field, string>>

interface Props {
  categories: Category[]
  /** Values to start with (e.g. the series from the chosen occurrence on), or null for a new series. */
  initial: RecurringTransactionInput | null
  defaultStartDate: string
  title: string
  submitLabel: string
  onSubmit: (input: RecurringTransactionInput) => Promise<unknown>
  onCancel?: () => void
}

type Kind = 'expense' | 'income'

function toAmountText(amount: number): string {
  return Math.abs(amount).toFixed(2).replace('.', ',')
}

export function RecurringTransactionForm({
  categories,
  initial,
  defaultStartDate,
  title,
  submitLabel,
  onSubmit,
  onCancel,
}: Props) {
  // The amount is entered positive; the toggle sets the sign (R11).
  const [kind, setKind] = useState<Kind>(initial && initial.amount > 0 ? 'income' : 'expense')
  const [amount, setAmount] = useState(initial ? toAmountText(initial.amount) : '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [categoryId, setCategoryId] = useState(initial ? String(initial.categoryId) : '')
  const [interval, setIntervalValue] = useState<RecurringInterval>(initial?.interval ?? 'MONTHLY')
  const [startDate, setStartDate] = useState(initial?.startDate ?? defaultStartDate)
  const [endDate, setEndDate] = useState(initial?.endDate ?? '')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function validate(): { errors: Errors; input: RecurringTransactionInput | null } {
    const found: Errors = {}
    const parsedAmount = parseAmountInput(amount)
    if (amount.trim() === '') {
      found.amount = 'Der Betrag ist Pflicht.'
    } else if (parsedAmount === null) {
      found.amount = 'Bitte einen Betrag wie 12,50 eingeben (höchstens 2 Nachkommastellen).'
    } else if (parsedAmount <= 0) {
      found.amount = 'Der Betrag muss größer als 0 sein.'
    }
    if (description.trim() === '') {
      found.description = 'Die Beschreibung ist Pflicht.'
    } else if (description.length > 100) {
      found.description = 'Die Beschreibung darf höchstens 100 Zeichen lang sein.'
    }
    if (categoryId === '') {
      found.categoryId = 'Die Kategorie ist Pflicht.'
    }
    if (startDate === '') {
      found.startDate = 'Das Startdatum ist Pflicht.'
    } else if (endDate !== '' && endDate < startDate) {
      // ISO dates compare correctly as strings
      found.endDate = 'Das Enddatum darf nicht vor dem Startdatum liegen.'
    }
    if (Object.keys(found).length > 0 || parsedAmount === null) {
      return { errors: found, input: null }
    }
    return {
      errors: found,
      input: {
        amount: kind === 'expense' ? -parsedAmount : parsedAmount,
        description: description.trim(),
        categoryId: Number(categoryId),
        interval,
        startDate,
        endDate: endDate === '' ? null : endDate,
      },
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setGeneralError(null)
    const result = validate()
    setErrors(result.errors)
    if (!result.input) {
      return
    }

    setSubmitting(true)
    try {
      await onSubmit(result.input)
      if (!initial) {
        setAmount('')
        setDescription('')
        setEndDate('')
      }
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(Object.fromEntries(error.problem.errors.map((e) => [e.field, e.message])))
      } else {
        setGeneralError(error instanceof Error ? error.message : 'Speichern fehlgeschlagen.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="transaction-form" noValidate onSubmit={handleSubmit}>
      <h3>{title}</h3>

      <fieldset className="sign-toggle">
        <legend>Art</legend>
        <label>
          <input type="radio" name="kind" checked={kind === 'expense'} onChange={() => setKind('expense')} />
          Ausgabe
        </label>
        <label>
          <input type="radio" name="kind" checked={kind === 'income'} onChange={() => setKind('income')} />
          Einnahme
        </label>
      </fieldset>

      <label>
        Betrag je Vorkommen (€)
        <input
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          aria-invalid={errors.amount ? true : undefined}
        />
        {errors.amount && <span className="error">{errors.amount}</span>}
      </label>

      <label>
        Beschreibung
        <input
          value={description}
          maxLength={100}
          onChange={(e) => setDescription(e.target.value)}
          aria-invalid={errors.description ? true : undefined}
        />
        {errors.description && <span className="error">{errors.description}</span>}
      </label>

      <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} error={errors.categoryId} />

      <label>
        Intervall
        <select value={interval} onChange={(e) => setIntervalValue(e.target.value as RecurringInterval)}>
          {(Object.keys(INTERVAL_LABELS) as RecurringInterval[]).map((value) => (
            <option key={value} value={value}>
              {INTERVAL_LABELS[value]}
            </option>
          ))}
        </select>
        {errors.interval && <span className="error">{errors.interval}</span>}
      </label>

      <label>
        Startdatum (erste Abbuchung)
        <input
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          aria-invalid={errors.startDate ? true : undefined}
        />
        {errors.startDate && <span className="error">{errors.startDate}</span>}
      </label>

      <label>
        Enddatum (optional)
        <input
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          aria-invalid={errors.endDate ? true : undefined}
        />
        {errors.endDate && <span className="error">{errors.endDate}</span>}
      </label>

      {generalError && <p className="error">{generalError}</p>}

      <div>
        <button type="submit" disabled={submitting}>
          {submitLabel}
        </button>{' '}
        {onCancel && (
          <button type="button" onClick={onCancel}>
            Abbrechen
          </button>
        )}
      </div>
    </form>
  )
}
