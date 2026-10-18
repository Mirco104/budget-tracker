import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, CategoryGroup, Transaction, TransactionInput } from '../api/types'
import { parseAmountInput } from '../format'

type Field = 'amount' | 'description' | 'date' | 'categoryId'
type Errors = Partial<Record<Field, string>>

const GROUP_LABELS: Record<CategoryGroup, string> = {
  FIXED: 'Fixkosten',
  FLEXIBLE: 'Flexible Ausgaben',
  SAVINGS: 'Sparen & Investieren',
  REMAINDER: 'Rest',
}

interface Props {
  categories: Category[]
  /** Transaction being edited, or null when creating a new one. */
  transaction: Transaction | null
  defaultDate: string
  onSubmit: (input: TransactionInput) => Promise<unknown>
  onCancel?: () => void
}

function toAmountText(amount: number): string {
  return amount.toFixed(2).replace('.', ',')
}

export function TransactionForm({ categories, transaction, defaultDate, onSubmit, onCancel }: Props) {
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
      input: { amount: parsedAmount, description: description.trim(), date, categoryId: Number(categoryId) },
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

  const income = categories.filter((c) => c.type === 'INCOME')
  const expenseGroups = (Object.keys(GROUP_LABELS) as CategoryGroup[]).map((group) => ({
    group,
    items: categories.filter((c) => c.type === 'EXPENSE' && c.group === group),
  }))

  return (
    <form className="transaction-form" noValidate onSubmit={handleSubmit}>
      <h3>{transaction ? 'Transaktion bearbeiten' : 'Neue Transaktion'}</h3>

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

      <label>
        Kategorie
        <select
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
          aria-invalid={errors.categoryId ? true : undefined}
        >
          <option value="">Bitte wählen</option>
          <optgroup label="Einnahmen">
            {income.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </optgroup>
          {expenseGroups.map(({ group, items }) => (
            <optgroup key={group} label={GROUP_LABELS[group]}>
              {items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        {errors.categoryId && <span className="error">{errors.categoryId}</span>}
      </label>

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
