import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, RecurringInterval, RecurringTransactionInput, TransactionInput } from '../api/types'
import { parseAmountInput } from '../format'
import { INTERVAL_LABELS } from '../labels'
import { CategorySelect } from './CategorySelect'

type Field = 'amount' | 'description' | 'date' | 'categoryId' | 'repeat' | 'endDate'
type Errors = Partial<Record<Field, string>>

/** NONE is a single transaction; an interval makes it a recurring one (R12). */
export type Repeat = 'NONE' | RecurringInterval

/** Values the form starts with. amount is signed (R11); for a series, date is the first occurrence. */
export interface TransactionFormValues {
  amount: number
  description: string
  date: string
  categoryId: number
  repeat: Repeat
  endDate: string | null
}

/** What the form submits: a single transaction or a series, depending on "Wiederholen". */
export type TransactionFormResult =
  | { kind: 'single'; input: TransactionInput }
  | { kind: 'recurring'; input: RecurringTransactionInput }

/**
 * none: no "Wiederholen" (editing a single transaction or one occurrence).
 * optional: "nie" or an interval (new transaction).
 * required: only intervals (editing a series from an occurrence on).
 */
export type RepeatOption = 'none' | 'optional' | 'required'

interface Props {
  categories: Category[]
  /** Values to start with, or null for an empty form. */
  initial: TransactionFormValues | null
  defaultDate: string
  repeatOption: RepeatOption
  title: string
  submitLabel: string
  onSubmit: (result: TransactionFormResult) => Promise<unknown>
  onCancel?: () => void
}

type Kind = 'expense' | 'income'

// The series API names the first occurrence startDate; the form shows it in the date field.
const SERVER_FIELDS: Record<string, Field> = { startDate: 'date', interval: 'repeat' }

function toAmountText(amount: number): string {
  return Math.abs(amount).toFixed(2).replace('.', ',')
}

export function TransactionForm({
  categories,
  initial,
  defaultDate,
  repeatOption,
  title,
  submitLabel,
  onSubmit,
  onCancel,
}: Props) {
  // The amount is entered positive; the toggle sets the sign (R11).
  const [kind, setKind] = useState<Kind>(initial && initial.amount > 0 ? 'income' : 'expense')
  const [amount, setAmount] = useState(initial ? toAmountText(initial.amount) : '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [date, setDate] = useState(initial?.date ?? defaultDate)
  const [categoryId, setCategoryId] = useState(initial ? String(initial.categoryId) : '')
  const [repeat, setRepeat] = useState<Repeat>(initial?.repeat ?? (repeatOption === 'required' ? 'MONTHLY' : 'NONE'))
  const [endDate, setEndDate] = useState(initial?.endDate ?? '')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const recurring = repeat !== 'NONE'

  function validate(): { errors: Errors; result: TransactionFormResult | null } {
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
    if (date === '') {
      found.date = 'Das Datum ist Pflicht.'
    } else if (recurring && endDate !== '' && endDate < date) {
      // ISO dates compare correctly as strings
      found.endDate = 'Das Enddatum darf nicht vor dem Startdatum liegen.'
    }
    if (categoryId === '') {
      found.categoryId = 'Die Kategorie ist Pflicht.'
    }
    if (Object.keys(found).length > 0 || parsedAmount === null) {
      return { errors: found, result: null }
    }
    const signedAmount = kind === 'expense' ? -parsedAmount : parsedAmount
    const common = { amount: signedAmount, description: description.trim(), categoryId: Number(categoryId) }
    return {
      errors: found,
      result: recurring
        ? {
            kind: 'recurring',
            input: { ...common, interval: repeat, startDate: date, endDate: endDate === '' ? null : endDate },
          }
        : { kind: 'single', input: { ...common, date } },
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setGeneralError(null)
    const { errors: found, result } = validate()
    setErrors(found)
    if (!result) {
      return
    }

    setSubmitting(true)
    try {
      await onSubmit(result)
      if (!initial) {
        setAmount('')
        setDescription('')
        setEndDate('')
        setRepeat('NONE')
      }
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(Object.fromEntries(error.problem.errors.map((e) => [SERVER_FIELDS[e.field] ?? e.field, e.message])))
      } else {
        setGeneralError(error instanceof Error ? error.message : 'Speichern fehlgeschlagen.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const intervals = Object.keys(INTERVAL_LABELS) as RecurringInterval[]

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
        Betrag (€)
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

      <label>
        {recurring ? 'Datum (erste Abbuchung)' : 'Datum'}
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          aria-invalid={errors.date ? true : undefined}
        />
        {errors.date && <span className="error">{errors.date}</span>}
      </label>

      <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} error={errors.categoryId} />

      {repeatOption !== 'none' && (
        <label>
          Wiederholen
          <select value={repeat} onChange={(e) => setRepeat(e.target.value as Repeat)}>
            {repeatOption === 'optional' && <option value="NONE">nie</option>}
            {intervals.map((value) => (
              <option key={value} value={value}>
                {INTERVAL_LABELS[value]}
              </option>
            ))}
          </select>
          {errors.repeat && <span className="error">{errors.repeat}</span>}
        </label>
      )}

      {recurring && (
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
      )}

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
