import { useState, type FormEvent } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { useCreateSavingGoal, useSavingGoals } from '../api/goals'
import { ApiError } from '../api/client'
import { useCreatePlannedItem, useDeletePlannedItem, useUpdatePlannedItem } from '../api/plan'
import type { Category, EntryMode, PlanGroup, PlannedItem, PlannedItemInput, RhythmUnit } from '../api/types'
import { formatEuro, formatMonth, parseAmountInput, shiftMonth } from '../format'
import { RHYTHM_PRESETS } from '../labels'
import { anchorFromDay, dayOfAnchor } from '../planning'
import { Dialog } from './ui/Dialog'
import { InlineCreate } from './ui/InlineCreate'
import { Segmented } from './ui/Segmented'

type Kind = 'income' | 'expense' | 'saving'
type Field = 'name' | 'amount' | 'categoryId' | 'rhythm' | 'day' | 'anchorDate' | 'validFrom' | 'accountId'
type Errors = Partial<Record<Field, string>>

const KINDS: { value: Kind; label: string }[] = [
  { value: 'expense', label: 'Ausgabe' },
  { value: 'income', label: 'Einnahme' },
  { value: 'saving', label: 'Sparen' },
]

const ENTRY_MODES: { value: EntryMode; label: string }[] = [
  { value: 'AUTO', label: 'automatisch am Termin' },
  { value: 'TICK', label: 'beim Abhaken' },
  { value: 'MANUAL', label: 'selbst anlegen' },
]

const MONTH_NAMES = [
  'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
]

/** The value of the field "Wie oft": a preset as "UNIT:COUNT", or NONE for "ohne festen Termin". */
const NONE = 'NONE'

const SERVER_FIELDS: Record<string, Field> = { rhythmCount: 'rhythm', rhythmUnit: 'rhythm' }

function kindOf(item: PlannedItem | null, group: PlanGroup | undefined): Kind {
  const g = item?.group ?? group
  return g === 'INCOME' ? 'income' : g === 'SAVING' ? 'saving' : 'expense'
}

// Decision 51: income and fixed costs automatically, saving when ticked off, budgets by the user
function defaultMode(kind: Kind, rhythm: string): EntryMode {
  if (rhythm === NONE) {
    return 'MANUAL'
  }
  return kind === 'saving' ? 'TICK' : 'AUTO'
}

interface Props {
  categories: Category[]
  /** The version to edit, or null for a new item. */
  item: PlannedItem | null
  /** The month the page shows: the default of "Gilt ab". */
  month: string
  /** The group of a new item (from the "+" of a group). */
  group?: PlanGroup
  /** Prefilled values of a new item, e.g. "Zum Sparen" with the unplanned rest. */
  preset?: { name?: string; amount?: number; categoryId?: number; savingGoalId?: number }
  onClose: () => void
  onDone: (message: string) => void
}

/**
 * Edit a planned item (concept 3.1): name, amount per payment, "Wie oft", day or due month, category, account, how it
 * is entered, working days and "Gilt ab". The monthly share is calculated by the backend (P2) and shown for a saved
 * item. A change asks from which month it applies; earlier months keep their plan.
 */
export function PlannedItemDialog({ categories, item, month, group, preset, onClose, onDone }: Props) {
  const create = useCreatePlannedItem()
  const update = useUpdatePlannedItem()
  const remove = useDeletePlannedItem()
  const bankAccounts = useBankAccounts().data ?? []
  const goals = useSavingGoals().data ?? []
  const createGoal = useCreateSavingGoal()

  const initialRhythm = item
    ? item.rhythmUnit === null
      ? NONE
      : `${item.rhythmUnit}:${item.rhythmCount}`
    : group === 'BUDGET'
      ? NONE
      : 'MONTH:1'
  const anchor = item?.anchorDate ? dayOfAnchor(item.anchorDate) : null
  const [kind, setKind] = useState<Kind>(kindOf(item, group))
  const [name, setName] = useState(item?.name ?? preset?.name ?? '')
  const [amount, setAmount] = useState(
    item ? Math.abs(item.amount).toFixed(2).replace('.', ',') : preset?.amount ? preset.amount.toFixed(2).replace('.', ',') : '',
  )
  const [categoryId, setCategoryId] = useState(
    item?.categoryId ? String(item.categoryId) : preset?.categoryId ? String(preset.categoryId) : '',
  )
  const [accountId, setAccountId] = useState(item ? String(item.accountId) : '')
  const [goalId, setGoalId] = useState(
    item?.savingGoalId ? String(item.savingGoalId) : preset?.savingGoalId ? String(preset.savingGoalId) : '',
  )
  const [creatingGoal, setCreatingGoal] = useState(false)
  const [rhythm, setRhythm] = useState(initialRhythm)
  const [day, setDay] = useState(anchor ? String(anchor.day) : '1')
  const [dueMonth, setDueMonth] = useState(anchor ? anchor.month : Number(month.slice(5, 7)))
  const [firstDate, setFirstDate] = useState(item?.anchorDate ?? `${month}-01`)
  const [entryMode, setEntryMode] = useState<EntryMode>(item?.entryMode ?? defaultMode(kindOf(item, group), initialRhythm))
  const [workdaysOnly, setWorkdaysOnly] = useState(item?.workdaysOnly ?? false)
  // A change applies from the month the page shows, never before the version starts
  const [validFrom, setValidFrom] = useState(item && item.validFrom > month ? item.validFrom : month)
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const [unit, count] = rhythm === NONE ? [null, null] : (rhythm.split(':') as [RhythmUnit, string])
  const rhythmCount = count === null ? null : Number(count)
  const rare = unit === 'MONTH' && rhythmCount !== null && rhythmCount > 1

  function chooseRhythm(next: string) {
    setRhythm(next)
    // Without a date nothing is entered automatically
    if (next === NONE && entryMode === 'AUTO') {
      setEntryMode('MANUAL')
    } else if (rhythm === NONE && next !== NONE && entryMode === 'MANUAL' && kind !== 'expense') {
      setEntryMode(defaultMode(kind, next))
    }
  }

  function chooseKind(next: Kind) {
    setKind(next)
    if (!item) {
      setEntryMode(defaultMode(next, rhythm))
    }
  }

  function anchorDate(): string | null {
    if (unit === null) {
      return null
    }
    if (unit === 'WEEK') {
      return firstDate
    }
    return anchorFromDay(validFrom, Number(day), rare ? dueMonth : null)
  }

  function validate(): Errors {
    const found: Errors = {}
    const parsed = parseAmountInput(amount)
    if (name.trim() === '') {
      found.name = 'Der Name ist Pflicht.'
    }
    if (amount.trim() === '') {
      found.amount = 'Der Betrag ist Pflicht.'
    } else if (parsed === null || parsed <= 0) {
      found.amount = 'Bitte einen Betrag größer als 0 wie 12,50 eingeben.'
    }
    if (kind !== 'saving' && categoryId === '') {
      found.categoryId = 'Die Kategorie ist Pflicht.'
    }
    if (unit === 'MONTH') {
      const d = Number(day)
      if (!Number.isInteger(d) || d < 1 || d > 31) {
        found.day = 'Bitte einen Tag von 1 bis 31 eingeben.'
      }
    }
    if (unit === 'WEEK' && firstDate === '') {
      found.anchorDate = 'Das Datum ist Pflicht.'
    }
    if (!/^\d{4}-\d{2}$/.test(validFrom)) {
      found.validFrom = '„Gilt ab“ ist Pflicht.'
    }
    return found
  }

  function input(): PlannedItemInput {
    const parsed = parseAmountInput(amount) ?? 0
    return {
      name: name.trim(),
      amount: kind === 'income' ? parsed : -parsed,
      saving: kind === 'saving',
      categoryId: kind === 'saving' && categoryId === '' ? null : Number(categoryId),
      ...(accountId === '' ? {} : { accountId: Number(accountId) }),
      rhythmUnit: unit,
      rhythmCount,
      anchorDate: anchorDate(),
      entryMode,
      workdaysOnly: unit !== null && workdaysOnly,
      validFrom,
      savingGoalId: kind === 'saving' && goalId !== '' ? Number(goalId) : null,
    }
  }

  async function run(action: () => Promise<unknown>, message: string) {
    setGeneralError(null)
    setSubmitting(true)
    try {
      await action()
      onDone(message)
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(Object.fromEntries(error.problem.errors.map((e) => [SERVER_FIELDS[e.field] ?? e.field, e.message])))
      } else {
        setGeneralError(error instanceof Error ? error.message : 'Speichern fehlgeschlagen.')
      }
      setSubmitting(false)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) {
      return
    }
    if (item) {
      void run(
        () => update.mutateAsync({ id: item.id, input: input() }),
        validFrom === item.validFrom ? 'Posten gespeichert.' : `Änderung gilt ab ${formatMonth(validFrom)}.`,
      )
    } else {
      void run(() => create.mutateAsync(input()), 'Posten angelegt.')
    }
  }

  const options = categories.filter((c) => !c.archived || String(c.id) === categoryId)
  const accountOptions = bankAccounts.filter((a) => !a.archived || String(a.id) === accountId)
  const defaultAccount = bankAccounts.find((a) => a.defaultAccount)
  const title = item ? 'Posten bearbeiten' : 'Neuer Posten'

  return (
    <Dialog title={title} onClose={onClose}>
      <form className="booking-form item-form" noValidate onSubmit={handleSubmit}>
        <Segmented label="Art" options={KINDS} value={kind} onChange={chooseKind} className="booking-kind" />

        <label className="field">
          Name
          <input
            value={name}
            maxLength={100}
            placeholder={kind === 'income' ? 'z. B. Gehalt' : kind === 'saving' ? 'z. B. Notgroschen' : 'z. B. Miete'}
            onChange={(e) => setName(e.target.value)}
            aria-invalid={errors.name ? true : undefined}
          />
          {errors.name && <span className="error">{errors.name}</span>}
        </label>

        <div className="field-row">
          <label className="field">
            Wie oft
            <select value={rhythm} onChange={(e) => chooseRhythm(e.target.value)}>
              {RHYTHM_PRESETS.map((p) => (
                <option key={`${p.unit}:${p.count}`} value={`${p.unit}:${p.count}`}>
                  {p.label}
                </option>
              ))}
              {/* A version saved with another count keeps it */}
              {unit !== null && !RHYTHM_PRESETS.some((p) => `${p.unit}:${p.count}` === rhythm) && (
                <option value={rhythm}>
                  alle {count} {unit === 'WEEK' ? 'Wochen' : 'Monate'}
                </option>
              )}
              <option value={NONE}>ohne festen Termin</option>
            </select>
            {errors.rhythm && <span className="error">{errors.rhythm}</span>}
          </label>
          <label className="field">
            {unit === null ? 'Betrag im Monat' : 'Betrag pro Zahlung'}
            <input
              inputMode="decimal"
              placeholder="0,00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={errors.amount ? true : undefined}
            />
            {errors.amount && <span className="error">{errors.amount}</span>}
          </label>
        </div>

        {unit === 'MONTH' && (
          <div className="field-row">
            {rare && (
              <label className="field">
                Fällig im
                <select value={dueMonth} onChange={(e) => setDueMonth(Number(e.target.value))}>
                  {MONTH_NAMES.map((label, index) => (
                    <option key={label} value={index + 1}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="field">
              Am Tag
              <input
                inputMode="numeric"
                value={day}
                onChange={(e) => setDay(e.target.value)}
                aria-invalid={errors.day ? true : undefined}
              />
              {errors.day && <span className="error">{errors.day}</span>}
            </label>
          </div>
        )}
        {unit === 'WEEK' && (
          <label className="field">
            Erster Termin
            <input
              type="date"
              value={firstDate}
              onChange={(e) => setFirstDate(e.target.value)}
              aria-invalid={errors.anchorDate ? true : undefined}
            />
            {errors.anchorDate && <span className="error">{errors.anchorDate}</span>}
          </label>
        )}
        {item && item.rhythmUnit !== null && (item.rhythmUnit === 'WEEK' || (item.rhythmCount ?? 1) > 1) && (
          <p className="hint">Monatsanteil: {formatEuro(item.monthlyShare)} (gespeicherter Stand)</p>
        )}

        {kind === 'saving' && (
          <div className="field">
            <span id="item-goal-label">Sparziel (optional)</span>
            <select
              aria-labelledby="item-goal-label"
              value={goalId}
              onChange={(e) => (e.target.value === 'new' ? setCreatingGoal(true) : setGoalId(e.target.value))}
            >
              <option value="">ohne Sparziel</option>
              {goals.map((goal) => (
                <option key={goal.id} value={goal.id}>
                  {goal.name}
                </option>
              ))}
              <option value="new">+ Neues Sparziel …</option>
            </select>
            {creatingGoal && (
              <InlineCreate
                label="Neues Sparziel"
                placeholder="z. B. Notgroschen"
                onCreate={async (goalName) => {
                  const goal = await createGoal.mutateAsync({ name: goalName })
                  setGoalId(String(goal.id))
                  setCreatingGoal(false)
                }}
                onCancel={() => setCreatingGoal(false)}
              />
            )}
          </div>
        )}

        <label className="field">
          Kategorie{kind === 'saving' ? ' (optional)' : ''}
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            aria-invalid={errors.categoryId ? true : undefined}
          >
            <option value="">{kind === 'saving' ? 'ohne Kategorie' : 'Kategorie wählen …'}</option>
            {options.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {errors.categoryId && <span className="error">{errors.categoryId}</span>}
        </label>

        {accountOptions.length > 1 && (
          <label className="field">
            Konto
            <select
              value={accountId === '' ? String(defaultAccount?.id ?? '') : accountId}
              onChange={(e) => setAccountId(e.target.value)}
            >
              {accountOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        )}

        <div className="field-row">
          <label className="field">
            Eintragen
            <select value={entryMode} onChange={(e) => setEntryMode(e.target.value as EntryMode)}>
              {ENTRY_MODES.filter((m) => unit !== null || m.value !== 'AUTO').map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            Gilt ab
            <input
              type="month"
              value={validFrom}
              onChange={(e) => setValidFrom(e.target.value)}
              aria-invalid={errors.validFrom ? true : undefined}
            />
            {errors.validFrom && <span className="error">{errors.validFrom}</span>}
          </label>
        </div>
        {unit !== null && (
          <label className="check-field">
            <input type="checkbox" checked={workdaysOnly} onChange={(e) => setWorkdaysOnly(e.target.checked)} />
            Nur an Werktagen (am Wochenende oder Feiertag am nächsten Werktag)
          </label>
        )}
        {item && validFrom !== item.validFrom && (
          <p className="hint">
            Die Änderung gilt ab {formatMonth(validFrom)}; bis {formatMonth(shiftMonth(validFrom, -1))} bleibt der
            bisherige Stand.
          </p>
        )}

        {generalError && (
          <p className="error" role="alert">
            {generalError}
          </p>
        )}

        {confirmingDelete && item ? (
          <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
            <span>Ab {formatMonth(validFrom)} beenden oder den Posten ganz löschen? Buchungen bleiben.</span>
            <div className="dialog-buttons">
              <button type="button" onClick={() => setConfirmingDelete(false)} disabled={submitting}>
                Zurück
              </button>
              <button
                type="button"
                onClick={() =>
                  void run(() => remove.mutateAsync({ id: item.id, from: validFrom }), `Posten endet vor ${formatMonth(validFrom)}.`)
                }
                disabled={submitting}
              >
                Ab {formatMonth(validFrom)} beenden
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => void run(() => remove.mutateAsync({ id: item.id }), 'Posten gelöscht.')}
                disabled={submitting}
              >
                Ganz löschen
              </button>
            </div>
          </div>
        ) : (
          <div className="dialog-actions">
            <div>
              {item && (
                <button type="button" className="danger" onClick={() => setConfirmingDelete(true)} disabled={submitting}>
                  Löschen
                </button>
              )}
            </div>
            <div className="dialog-buttons">
              <button type="button" onClick={onClose}>
                Abbrechen
              </button>
              <button type="submit" disabled={submitting}>
                Speichern
              </button>
            </div>
          </div>
        )}
      </form>
    </Dialog>
  )
}
