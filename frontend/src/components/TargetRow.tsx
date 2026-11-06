import { useEffect, useId, useState, type FocusEvent, type FormEvent, type KeyboardEvent } from 'react'
import { ApiError } from '../api/client'
import type { Category, TargetInput } from '../api/types'
import { formatEuro, formatMonth, formatMonthName, parseAmountInput } from '../format'
import { INTERVAL_LABELS_BY_MONTHS } from '../labels'
import { euroToPercent, percentToEuro } from '../planning'
import { CategoryIcon } from './ui/CategoryIcon'
import { Icon } from './ui/Icon'
import { Segmented } from './ui/Segmented'

export type TargetUnit = 'eur' | 'pct'

interface Props {
  category: Category
  /** The month "Gilt ab" the target is saved for. */
  month: string
  /** The base for the input in percent, Ø or minimum income (US7a); null without one. */
  base: number | null
  /** "Ø Einnahmen" or "Mindesteinnahmen", for the equivalent below the field. */
  baseName: string
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

/** How long "Gespeichert ab …" stays below the field. */
const SAVED_NOTE_MS = 3000

const percentFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 })

function euroText(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

function percentText(value: number): string {
  return String(value).replace('.', ',')
}

function hasBase(base: number | null): base is number {
  return base !== null && base > 0
}

/** The euro value the field stands for; null when it is empty or cannot be read. */
function euroOf(unit: TargetUnit, text: string, base: number | null): number | null {
  const parsed = parseAmountInput(text)
  if (parsed === null) {
    return null
  }
  return unit === 'eur' ? parsed : percentToEuro(parsed, base)
}

/**
 * Target of one category (US2): one field with a switch € / %. Only euro is saved (decision 36); percent is an input
 * helper on the chosen base, and the other value is always shown below the field. Switching the unit converts the
 * number. There is no save button: a change is saved when the focus leaves the row or on Enter. Below the name the
 * fixed costs of the category (R25), expandable to its recurring expenses, with "übernehmen".
 */
export function TargetRow({
  category,
  month,
  base,
  baseName,
  previousActual,
  previousIsIncome,
  previousMonth,
  onSubmit,
}: Props) {
  const [unit, setUnit] = useState<TargetUnit>('eur')
  const [text, setText] = useState(category.targetAmount === null ? '' : euroText(category.targetAmount))
  // The euro value as last saved; leaving the row without a change sends nothing.
  const [savedAmount, setSavedAmount] = useState(category.targetAmount)
  const [error, setError] = useState<string | null>(null)
  const [savedNote, setSavedNote] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [fixedOpen, setFixedOpen] = useState(false)
  const [previousBase, setPreviousBase] = useState(base)
  const itemsId = useId()

  // A new base (the user picked the other tile) keeps the euro value: the percent in the field is converted
  if (base !== previousBase) {
    setPreviousBase(base)
    if (unit === 'pct') {
      const euro = euroOf('pct', text, previousBase)
      if (!hasBase(base)) {
        setUnit('eur')
        setText(euro === null ? text : euroText(euro))
      } else if (euro !== null) {
        setText(percentText(euroToPercent(euro, base) as number))
      }
    }
  }

  useEffect(() => {
    if (!savedNote) {
      return
    }
    const timer = setTimeout(() => setSavedNote(false), SAVED_NOTE_MS)
    return () => clearTimeout(timer)
  }, [savedNote])

  const empty = text.trim() === ''
  const euro = euroOf(unit, text, base)
  const fixedCosts = category.fixedCosts
  const belowFixedCosts = fixedCosts !== null && euro !== null && euro < fixedCosts
  const canTakeFixedCosts = fixedCosts !== null && (empty || belowFixedCosts)

  function validate(): { error: string | null; amount: number | null } {
    if (empty) {
      return { error: null, amount: null }
    }
    const parsed = parseAmountInput(text)
    if (unit === 'eur') {
      if (parsed === null) {
        return { error: 'Bitte einen Betrag wie 12,50 eingeben (höchstens 2 Nachkommastellen).', amount: null }
      }
      if (parsed < 0) {
        return { error: 'Der Soll-Betrag darf nicht negativ sein.', amount: null }
      }
      return { error: null, amount: parsed }
    }
    if (parsed === null) {
      return { error: 'Bitte einen Prozentwert wie 15 oder 12,5 eingeben (höchstens 2 Nachkommastellen).', amount: null }
    }
    if (parsed < 0 || parsed > 100) {
      return { error: 'Der Prozentwert muss zwischen 0 und 100 % liegen.', amount: null }
    }
    return { error: null, amount: percentToEuro(parsed, base) }
  }

  async function submit(amount: number | null) {
    setSubmitting(true)
    setSavedNote(false)
    try {
      await onSubmit({ targetAmount: amount })
      setSavedAmount(amount)
      setSavedNote(true)
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

  async function save() {
    if (submitting) {
      return
    }
    const result = validate()
    setError(result.error)
    if (result.error !== null || result.amount === savedAmount) {
      return
    }
    await submit(result.amount)
  }

  function takeFixedCosts() {
    if (fixedCosts === null || submitting) {
      return
    }
    setUnit('eur')
    setText(euroText(fixedCosts))
    setError(null)
    void submit(fixedCosts)
  }

  function switchUnit(next: TargetUnit) {
    const current = euroOf(unit, text, base)
    setUnit(next)
    setError(null)
    if (current === null) {
      return
    }
    setText(next === 'eur' ? euroText(current) : percentText(euroToPercent(current, base) as number))
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

  // The other value is always shown (US2); without a base the percent has no value
  let equivalent = ''
  let equivalentMore = ''
  if (euro !== null) {
    if (unit === 'pct') {
      equivalent = `≙ ${formatEuro(euro)}`
      equivalentMore = ', wird so gespeichert'
    } else {
      const percent = euroToPercent(euro, base)
      equivalent = `≙ ${percent === null ? '–' : percentFormat.format(percent)} %`
      equivalentMore = ` der ${baseName}`
    }
  }

  const errorId = `target-error-${category.id}`
  const rowClass = ['plan-row', fixedCosts !== null ? 'has-fixed' : '', belowFixedCosts ? 'short' : '']
    .filter(Boolean)
    .join(' ')
  const previousText = previousIsIncome ? `+${formatEuro(previousActual)}` : formatEuro(previousActual)

  return (
    <form className={rowClass} aria-label={category.name} noValidate onSubmit={handleSubmit} onBlur={handleBlur}>
      <div className="plan-category">
        <CategoryIcon icon={category.icon} />
        <div>
          <div className="plan-name">{category.name}</div>
          {empty && <div className="plan-note no-target">kein Soll</div>}
          {!empty && category.targetValidFrom && category.targetValidFrom !== month && (
            <div className="plan-note set-from">gesetzt ab {formatMonth(category.targetValidFrom)}</div>
          )}
          <div className="plan-previous-mobile">
            Vormonat ({formatMonthName(previousMonth, true)}):{' '}
            <span className={previousIsIncome ? 'positive' : undefined}>{previousText}</span>
          </div>
        </div>
      </div>

      <div className="plan-input">
        <div className={error ? 'unit-input invalid' : 'unit-input'}>
          <input
            inputMode="decimal"
            enterKeyHint="done"
            aria-label={`Soll für ${category.name} in ${unit === 'eur' ? 'Euro' : `Prozent der ${baseName}`}`}
            placeholder={unit === 'eur' ? '0,00' : '0'}
            value={text}
            onChange={(event) => {
              setText(event.target.value)
              setSavedNote(false)
            }}
            onKeyDown={handleKeyDown}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
          />
          <Segmented
            label="Eingabe in"
            options={UNITS.map((option) => ({ ...option, disabled: option.value === 'pct' && !hasBase(base) }))}
            value={unit}
            onChange={switchUnit}
          />
        </div>
      </div>

      <div className="plan-meta" aria-live="polite">
        {error ? (
          <span className="error" id={errorId}>
            {error}
          </span>
        ) : (
          <>
            {equivalent && (
              <span className="plan-equivalent">
                {equivalent}
                <span className="plan-more">{equivalentMore}</span>
              </span>
            )}
            {submitting && <span className="plan-note">Speichert …</span>}
            {!submitting && savedNote && <span className="plan-note">Gespeichert ab {formatMonth(month)}.</span>}
            {belowFixedCosts && (
              <span className="plan-warning">unter den fixen Kosten von {formatEuro(fixedCosts)}</span>
            )}
          </>
        )}
      </div>

      {fixedCosts !== null && (
        <div className="plan-fixed">
          <button
            type="button"
            className="fixed-toggle"
            aria-expanded={fixedOpen}
            aria-controls={itemsId}
            onClick={() => setFixedOpen(!fixedOpen)}
          >
            <Icon name="repeat" size={14} />
            <span>
              fix {formatEuro(fixedCosts)}
              <span className="plan-more"> / Monat</span>
            </span>
            <Icon name={fixedOpen ? 'chevron-up' : 'chevron-down'} size={14} />
          </button>
          {canTakeFixedCosts && (
            <button
              type="button"
              className="link-button"
              aria-label={`Fixe Kosten ${formatEuro(fixedCosts)} als Soll für ${category.name} übernehmen`}
              onClick={takeFixedCosts}
            >
              übernehmen
            </button>
          )}
        </div>
      )}

      {fixedCosts !== null && fixedOpen && (
        <ul className="plan-fixed-items" id={itemsId}>
          {category.fixedCostItems.map((item) => (
            <li key={item.recurringId}>
              <span>{item.description}</span>
              <span className="amount">{formatEuro(item.monthly)} / Monat</span>
              {item.interval > 1 && (
                <span className="plan-fixed-note">
                  {formatEuro(item.amount)} {INTERVAL_LABELS_BY_MONTHS[item.interval] ?? `alle ${item.interval} Monate`}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="plan-previous">
        {/* The column header names the month for sighted users; screen readers get it here */}
        <span className="visually-hidden">Ist im Vormonat ({formatMonthName(previousMonth)}): </span>
        <span className={previousIsIncome ? 'positive' : undefined}>{previousText}</span>
      </div>
    </form>
  )
}
