import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, Transaction, TransactionInput } from '../api/types'
import { parseAmountInput } from '../format'
import { CategorySelect } from './CategorySelect'

type Field = 'amount' | 'description' | 'date' | 'categoryId'
type Errors = Partial<Record<Field, string>>

interface Props {
  categories: Category[]
  /** Transaction being edited, or null when creating a new one. */
  transaction: Transaction | null
  defaultDate: string
  onSubmit: (input: TransactionInput) => Promise<unknown>
  onCancel?: () => void
}

type Kind = 'expense' | 'income'

function toAmountText(amount: number): string {
  return Math.abs(amount).toFixed(2).replace('.', ',')
}

export function TransactionForm({ categories, transaction, defaultDate, onSubmit, onCancel }: Props) {
  // The amount is entered positive; the toggle sets the sign (R11).
  const [kind, setKind] = useState<Kind>(transaction && transaction.amount > 0 ? 'income' : 'expense')
  const [amount, setAmount] = useState(transaction ? toAmountText(transaction.amount) : '')
  const [description, setDescription] = useState(transaction?.description ?? '')
  const [date, setDate] = useState(transaction?.date ?? defaultDate)
  const [categoryId, setCategoryId] = useState(transaction ? String(transaction.categoryId) : '')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function validate(): { errors: Errors; input: TransactionInput | null } {
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
    }
    if (categoryId === '') {
      found.categoryId = 'Die Kategorie ist Pflicht.'
    }
    if (Object.keys(found).length > 0 || parsedAmount === null) {
      return { errors: found, input: null }
    }
    return {
      errors: found,
      input: {
        amount: kind === 'expense' ? -parsedAmount : parsedAmount,
        description: description.trim(), date,
        categoryId: Number(categoryId),
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
      if (!transaction) {
        setAmount('')
        setDescription('')
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
      <h3>{transaction ? 'Transaktion bearbeiten' : 'Neue Transaktion'}</h3>

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
        Datum
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          aria-invalid={errors.date ? true : undefined}
        />
        {errors.date && <span className="error">{errors.date}</span>}
      </label>

      <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} error={errors.categoryId} />

      {generalError && <p className="error">{generalError}</p>}

      <div>
        <button type="submit" disabled={submitting}>
          {transaction ? 'Speichern' : 'Hinzufügen'}
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
