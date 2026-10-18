import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, YearlyTransaction, YearlyTransactionInput } from '../api/types'
import { parseAmountInput } from '../format'
import { CategorySelect } from './CategorySelect'

type Field = 'amountPerYear' | 'description' | 'dueDate' | 'validFrom' | 'validTo' | 'categoryId'
type Errors = Partial<Record<Field, string>>

interface Props {
  categories: Category[]
  /** Yearly transaction being edited, or null when creating a new one. */
  transaction: YearlyTransaction | null
  /** Month ("YYYY-MM") preset as "Gültig ab" for a new entry. */
  defaultValidFrom: string
  onSubmit: (input: YearlyTransactionInput) => Promise<unknown>
  onCancel?: () => void
}

function toAmountText(amount: number): string {
  return amount.toFixed(2).replace('.', ',')
}

export function YearlyTransactionForm({ categories, transaction, defaultValidFrom, onSubmit, onCancel }: Props) {
  const [amount, setAmount] = useState(transaction ? toAmountText(transaction.amountPerYear) : '')
  const [description, setDescription] = useState(transaction?.description ?? '')
  const [validFrom, setValidFrom] = useState(transaction?.validFrom ?? defaultValidFrom)
  const [validTo, setValidTo] = useState(transaction?.validTo ?? '')
  const [dueDate, setDueDate] = useState(transaction?.dueDate ?? '')
  const [categoryId, setCategoryId] = useState(transaction ? String(transaction.categoryId) : '')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function validate(): { errors: Errors; input: YearlyTransactionInput | null } {
    const found: Errors = {}
    const parsedAmount = parseAmountInput(amount)
    if (amount.trim() === '') {
      found.amountPerYear = 'Der Betrag ist Pflicht.'
    } else if (parsedAmount === null) {
      found.amountPerYear = 'Bitte einen Betrag wie 120,00 eingeben (höchstens 2 Nachkommastellen).'
    } else if (parsedAmount <= 0) {
      found.amountPerYear = 'Der Betrag muss größer als 0 sein.'
    }
    if (description.trim() === '') {
      found.description = 'Die Beschreibung ist Pflicht.'
    } else if (description.length > 100) {
      found.description = 'Die Beschreibung darf höchstens 100 Zeichen lang sein.'
    }
    if (validFrom === '') {
      found.validFrom = 'Der Monat „gültig ab“ ist Pflicht.'
    } else if (validTo !== '' && validTo < validFrom) {
      // "YYYY-MM" strings sort like months
      found.validTo = 'Der Monat „gültig bis“ darf nicht vor „gültig ab“ liegen.'
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
        amountPerYear: parsedAmount,
        description: description.trim(),
        dueDate: dueDate === '' ? null : dueDate,
        validFrom,
        validTo: validTo === '' ? null : validTo,
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
        setDueDate('')
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
      <h3>{transaction ? 'Jährliche Transaktion bearbeiten' : 'Neue jährliche Transaktion'}</h3>

      <label>
        Betrag pro Jahr (€)
        <input
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          aria-invalid={errors.amountPerYear ? true : undefined}
        />
        {errors.amountPerYear && <span className="error">{errors.amountPerYear}</span>}
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
        Gültig ab
        <input
          type="month"
          value={validFrom}
          onChange={(e) => setValidFrom(e.target.value)}
          aria-invalid={errors.validFrom ? true : undefined}
        />
        {errors.validFrom && <span className="error">{errors.validFrom}</span>}
      </label>

      <label>
        Gültig bis (optional)
        <input
          type="month"
          value={validTo}
          onChange={(e) => setValidTo(e.target.value)}
          aria-invalid={errors.validTo ? true : undefined}
        />
        {errors.validTo && <span className="error">{errors.validTo}</span>}
      </label>

      <label>
        Abbuchung regulär am (optional)
        <input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          aria-invalid={errors.dueDate ? true : undefined}
        />
        {errors.dueDate && <span className="error">{errors.dueDate}</span>}
      </label>

      <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} error={errors.categoryId} />

      <p className="hint">Wird mit einem Zwölftel des Jahresbetrags in jedem Monat der Gültigkeit angerechnet.</p>

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
