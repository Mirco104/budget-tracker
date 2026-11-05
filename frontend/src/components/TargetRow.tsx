import { useState, type FocusEvent, type FormEvent, type KeyboardEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, TargetInput } from '../api/types'
import { formatEuro, formatMonth, parseAmountInput } from '../format'
import { CategoryIcon } from './ui/CategoryIcon'
import { Segmented } from './ui/Segmented'

export type TargetUnit = 'eur' | 'pct'

interface Props {
  category: Category
  /** The month "Gilt ab" the target is saved for. */
  month: string
  /** The target in euro the backend calculated for the month (R3); null without a target or without Ø income. */
  targetEuro: number | null
  /**
   * The actual of the category in the month before "Gilt ab", as orientation (US2): its expenses I(k), or its net
   * N(k) when the category was income in that month (R2).
   */
  previousActual: number
  previousIsIncome: boolean
  previousMonth: string
  onSubmit: (input: TargetInput) => Promise<unknown>
}

const UNITS: { value: TargetUnit; label: string }[] = [
  { value: 'eur', label: '€' },
  { value: 'pct', label: '%' },
]

function toInputText(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

// The euro value wins when an old entry has both (R3), so the field starts with it.
function initialValue(category: Category): { unit: TargetUnit; text: string } {
  if (category.targetAmount !== null) {
    return { unit: 'eur', text: toInputText(category.targetAmount) }
  }
  if (category.targetPercent !== null) {
    return { unit: 'pct', text: toInputText(category.targetPercent * 100) }
  }
  return { unit: 'eur', text: '' }
}

/**
 * Target of one category: one field with a switch € / % (US2). Only the chosen value is saved, the other one is
 * cleared. There is no save button: a change is saved when the focus leaves the row or on Enter. Switching the unit
 * keeps the number and saves it in the new unit when the row is left.
 */
export function TargetRow({
  category,
  month,
  targetEuro,
  previousActual,
  previousIsIncome,
  previousMonth,
  onSubmit,
}: Props) {
  const [value, setValue] = useState(() => initialValue(category))
  // As last saved; leaving the row without a change sends nothing.
  const [savedValue, setSavedValue] = useState(value)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const empty = value.text.trim() === ''
  const unchanged = value.text.trim() === savedValue.text.trim() && (empty || value.unit === savedValue.unit)

  function validate(): { error: string | null; input: TargetInput | null } {
    if (empty) {
      return { error: null, input: { targetAmount: null, targetPercent: null } }
    }
    const parsed = parseAmountInput(value.text)
    if (value.unit === 'eur') {
      if (parsed === null) {
        return { error: 'Bitte einen Betrag wie 12,50 eingeben (höchstens 2 Nachkommastellen).', input: null }
      }
      if (parsed < 0) {
        return { error: 'Der Soll-Betrag darf nicht negativ sein.', input: null }
      }
      return { error: null, input: { targetAmount: parsed, targetPercent: null } }
    }
    if (parsed === null) {
      return { error: 'Bitte einen Prozentwert wie 15 oder 12,5 eingeben (höchstens 2 Nachkommastellen).', input: null }
    }
    if (parsed < 0 || parsed > 100) {
      return { error: 'Der Prozentwert muss zwischen 0 und 100 % liegen.', input: null }
    }
    // Rounded so float noise (33.33 / 100 = 0.33330000000000004) does not break the backend's 4 decimals.
    return { error: null, input: { targetAmount: null, targetPercent: Number((parsed / 100).toFixed(4)) } }
  }

  async function save() {
    if (submitting || unchanged) {
      return
    }
    setSaved(false)
    const result = validate()
    setError(result.error)
    if (!result.input) {
      return
    }

    setSubmitting(true)
    const sent = value
    try {
      await onSubmit(result.input)
      setSavedValue(sent)
      setSaved(true)
    } catch (caught) {
      if (caught instanceof ApiError && caught.problem.errors?.length) {
        setError(caught.problem.errors.map((e) => e.message).join(' '))
      } else {
        setError(caught instanceof Error ? caught.message : 'Speichern fehlgeschlagen.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  function handleBlur(event: FocusEvent<HTMLFormElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      void save()
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      void save()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void save()
  }

  function update(next: Partial<{ unit: TargetUnit; text: string }>) {
    setValue((current) => ({ ...current, ...next }))
    setSaved(false)
  }

  // The euro value of a percent target comes from the backend and is only shown for what is saved.
  const showEuro = unchanged && !empty && value.unit === 'pct' && targetEuro !== null
  const errorId = `target-error-${category.id}`

  return (
    <form className="plan-row" aria-label={category.name} noValidate onSubmit={handleSubmit} onBlur={handleBlur}>
      <div className="plan-category">
        <CategoryIcon icon={category.icon} />
        <div>
          <div className="plan-name">{category.name}</div>
          <div className="plan-note" aria-live="polite">
            {submitting && 'Speichert …'}
            {!submitting && saved && `Gespeichert ab ${formatMonth(month)}.`}
            {!submitting && !saved && empty && 'kein Soll'}
            {!submitting && !saved && showEuro && `entspricht ${formatEuro(targetEuro)}`}
            {!submitting &&
              !saved &&
              unchanged &&
              category.targetValidFrom &&
              category.targetValidFrom !== month &&
              `${empty || showEuro ? ' · ' : ''}gesetzt ab ${formatMonth(category.targetValidFrom)}`}
          </div>
        </div>
      </div>

      <div className="plan-input">
        <div className={error ? 'unit-input invalid' : 'unit-input'}>
          <input
            inputMode="decimal"
            enterKeyHint="done"
            aria-label={`Soll für ${category.name} in ${value.unit === 'eur' ? 'Euro' : 'Prozent'}`}
            placeholder={value.unit === 'eur' ? '0,00' : '0'}
            value={value.text}
            onChange={(event) => update({ text: event.target.value })}
            onKeyDown={handleKeyDown}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
          />
          <Segmented label="Einheit" options={UNITS} value={value.unit} onChange={(unit) => update({ unit })} />
        </div>
        {error && (
          <span className="error" id={errorId}>
            {error}
          </span>
        )}
      </div>

      <div className="plan-previous">
        <span className="plan-previous-label">Ist im {formatMonth(previousMonth)}: </span>
        <span className={previousIsIncome ? 'positive' : undefined}>
          {previousIsIncome ? `+${formatEuro(previousActual)}` : formatEuro(previousActual)}
        </span>
      </div>
    </form>
  )
}
