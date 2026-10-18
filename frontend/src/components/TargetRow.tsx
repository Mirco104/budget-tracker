import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, TargetInput } from '../api/types'
import { parseAmountInput } from '../format'

type Field = 'targetAmount' | 'targetPercent'
type Errors = Partial<Record<Field, string>>

interface Props {
  category: Category
  onSubmit: (input: TargetInput) => Promise<unknown>
}

function toInputText(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

export function TargetRow({ category, onSubmit }: Props) {
  const [amount, setAmount] = useState(category.targetAmount === null ? '' : toInputText(category.targetAmount))
  const [percent, setPercent] = useState(
    category.targetPercent === null ? '' : toInputText(category.targetPercent * 100),
  )
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function validate(): { errors: Errors; input: TargetInput | null } {
    const found: Errors = {}
    const parsedAmount = amount.trim() === '' ? null : parseAmountInput(amount)
    const parsedPercent = percent.trim() === '' ? null : parseAmountInput(percent)

    if (amount.trim() !== '' && parsedAmount === null) {
      found.targetAmount = 'Bitte einen Betrag wie 12,50 eingeben (höchstens 2 Nachkommastellen).'
    } else if (parsedAmount !== null && parsedAmount < 0) {
      found.targetAmount = 'Der Soll-Betrag darf nicht negativ sein.'
    }
    if (percent.trim() !== '' && parsedPercent === null) {
      found.targetPercent = 'Bitte einen Prozentwert wie 15 oder 12,5 eingeben (höchstens 2 Nachkommastellen).'
    } else if (parsedPercent !== null && (parsedPercent < 0 || parsedPercent > 100)) {
      found.targetPercent = 'Der Prozentwert muss zwischen 0 und 100 % liegen.'
    }
    if (Object.keys(found).length > 0) {
      return { errors: found, input: null }
    }
    return {
      errors: found,
      input: {
        targetAmount: parsedAmount,
        // Rounded so float noise (33.33 / 100 = 0.33330000000000004) does not break the backend's 4 decimals.
        targetPercent: parsedPercent === null ? null : Number((parsedPercent / 100).toFixed(4)),
      },
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setGeneralError(null)
    setSaved(false)
    const result = validate()
    setErrors(result.errors)
    if (!result.input) {
      return
    }

    setSubmitting(true)
    try {
      await onSubmit(result.input)
      setSaved(true)
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
    <form className="target-row" aria-label={category.name} noValidate onSubmit={handleSubmit}>
      <span className="target-name">{category.name}</span>

      <label>
        Soll (€)
        <input
          inputMode="decimal"
          value={amount}
          onChange={(e) => {
            setAmount(e.target.value)
            setSaved(false)
          }}
          aria-invalid={errors.targetAmount ? true : undefined}
        />
        {errors.targetAmount && <span className="error">{errors.targetAmount}</span>}
      </label>

      <label>
        Soll (%)
        <input
          inputMode="decimal"
          value={percent}
          onChange={(e) => {
            setPercent(e.target.value)
            setSaved(false)
          }}
          aria-invalid={errors.targetPercent ? true : undefined}
        />
        {errors.targetPercent && <span className="error">{errors.targetPercent}</span>}
      </label>

      <div className="target-actions">
        <button type="submit" disabled={submitting}>
          Speichern
        </button>
        {saved && <span className="hint"> Gespeichert.</span>}
        {amount.trim() !== '' && percent.trim() !== '' && <p className="hint">Euro-Wert hat Vorrang.</p>}
        {generalError && <p className="error">{generalError}</p>}
      </div>
    </form>
  )
}
