import { useEffect, useState, type FormEvent } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { ApiError } from '../api/client'
import {
  useCreateRecurringTransaction,
  useDeleteOccurrence,
  useRecurringTransactions,
  useUpdateOccurrenceFollowing,
  useUpdateOccurrenceThis,
} from '../api/recurring'
import { useBookingSuggestion } from '../api/suggestions'
import { useCreateTransaction, useDeleteTransaction, useTransactions, useUpdateTransaction } from '../api/transactions'
import type { Category, OccurrenceScope, RecurringInterval, Transaction } from '../api/types'
import { formatDate, parseAmountInput } from '../format'
import { INTERVAL_LABELS } from '../labels'
import { CategoryIcon } from './ui/CategoryIcon'
import { Dialog } from './ui/Dialog'
import { Segmented } from './ui/Segmented'

type Kind = 'expense' | 'income'
/** NONE is a single transaction; an interval makes it a series (R12). */
type Repeat = 'NONE' | RecurringInterval
type Field = 'amount' | 'description' | 'date' | 'categoryId' | 'repeat' | 'endDate' | 'accountId'
type Errors = Partial<Record<Field, string>>

interface Props {
  categories: Category[]
  /** The entry to edit, or null for a new booking. */
  entry: Transaction | null
  /** Date of a new booking; the last used categories come from the entries of its month (US27). */
  defaultDate: string
  onClose: () => void
  /** Called after saving or deleting, with the confirmation to show. */
  onDone: (message: string) => void
}

const KINDS: { value: Kind; label: string }[] = [
  { value: 'expense', label: 'Ausgabe' },
  { value: 'income', label: 'Einnahme' },
]

// The series API names the first occurrence startDate; the dialog shows it in the date field.
const SERVER_FIELDS: Record<string, Field> = { startDate: 'date', interval: 'repeat' }

const QUICK_PICKS = 4

/** The value of the category field for a transfer (R27). */
const TRANSFER = 'transfer'

function toAmountText(amount: number): string {
  return Math.abs(amount).toFixed(2).replace('.', ',')
}

function isOccurrence(entry: Transaction | null): entry is Transaction & { recurringId: number; occurrenceDate: string } {
  return entry?.kind === 'RECURRING' && entry.recurringId !== null && entry.occurrenceDate !== null
}

/** The value after it has not changed for the delay, so typing does not send a request per key. */
function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/** The categories last used with the sign, newest first, without archived ones and transfers (display only). */
function lastUsed(recent: Transaction[], categories: Category[], kind: Kind): Category[] {
  const result: Category[] = []
  const sorted = [...recent].sort((a, b) => b.date.localeCompare(a.date))
  for (const entry of sorted) {
    if ((entry.amount < 0) !== (kind === 'expense') || result.some((c) => c.id === entry.categoryId)) {
      continue
    }
    const category = categories.find((c) => c.id === entry.categoryId && !c.archived)
    if (category) {
      result.push(category)
    }
    if (result.length === QUICK_PICKS) {
      break
    }
  }
  return result
}

/**
 * Booking dialog (US27): Ausgabe / Einnahme, a large amount, description, category with the last used ones, date,
 * repeat and end date. Editing an occurrence asks whether the change is for this entry only or this and all
 * following ones (R13). Delete is in the dialog and asks once more.
 */
export function BookingDialog({ categories, entry, defaultDate, onClose, onDone }: Props) {
  const occurrence = isOccurrence(entry) ? entry : null
  // Already loaded on the transactions page; the overview loads it when the dialog opens
  const recent = useTransactions(defaultDate.slice(0, 7)).data ?? []
  // The series of an occurrence is active in the month of the occurrence; it fills "Dieser und alle folgenden".
  const series = useRecurringTransactions(occurrence ? occurrence.occurrenceDate.slice(0, 7) : '', occurrence !== null)
  const createTransaction = useCreateTransaction()
  const createSeries = useCreateRecurringTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const updateThis = useUpdateOccurrenceThis()
  const updateFollowing = useUpdateOccurrenceFollowing()
  const deleteOccurrence = useDeleteOccurrence()
  const bankAccounts = useBankAccounts().data ?? []

  // The amount is entered positive; the switch sets the sign (R11).
  const [kind, setKind] = useState<Kind>(entry && entry.amount > 0 ? 'income' : 'expense')
  const [amount, setAmount] = useState(entry ? toAmountText(entry.amount) : '')
  const [description, setDescription] = useState(entry?.description ?? '')
  // TRANSFER stands for a transfer between own accounts, which has no category (R27)
  const [chosenCategory, setChosenCategory] = useState(entry ? (entry.transfer ? TRANSFER : String(entry.categoryId)) : '')
  // US32: a new booking takes the suggested category until the user chooses one; an edited entry keeps its own
  const [categoryChosen, setCategoryChosen] = useState(entry !== null)
  // '' is the default account (R26)
  const [accountId, setAccountId] = useState(entry ? String(entry.accountId) : '')
  const [date, setDate] = useState(entry?.date ?? defaultDate)
  const [repeat, setRepeat] = useState<Repeat>('NONE')
  const [endDate, setEndDate] = useState('')
  const [scope, setScope] = useState<OccurrenceScope>('THIS')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const quick = lastUsed(recent, categories, kind)
  // US32: the suggestion for the description, asked for once typing pauses
  const suggestionText = useDebounced(description.trim(), 300)
  const suggestion = useBookingSuggestion(suggestionText, kind === 'income', !categoryChosen && suggestionText.length >= 2)
  const suggestedId = suggestion.data?.categoryId
  const suggested =
    !categoryChosen && suggestedId != null && categories.some((c) => c.id === suggestedId && !c.archived)
      ? String(suggestedId)
      : null
  const categoryId = categoryChosen ? chosenCategory : (suggested ?? '')

  const transfer = categoryId === TRANSFER
  const recurring = repeat !== 'NONE' && !transfer
  // Repeat is offered for a new booking and for a series from an occurrence on, not for a single entry or a transfer.
  const repeatOption: 'optional' | 'required' | 'none' = transfer
    ? 'none'
    : !entry
      ? 'optional'
      : occurrence && scope === 'FOLLOWING'
        ? 'required'
        : 'none'

  function chooseCategory(value: string) {
    setChosenCategory(value)
    setCategoryChosen(true)
  }

  const options = categories.filter((c) => !c.archived || String(c.id) === categoryId)
  // An archived account only when it is already chosen; one account needs no field (US30)
  const accountOptions = bankAccounts.filter((a) => !a.archived || String(a.id) === accountId)
  const defaultAccount = bankAccounts.find((a) => a.defaultAccount)

  function chooseScope(next: OccurrenceScope) {
    setScope(next)
    setConfirmingDelete(false)
    if (next === 'THIS') {
      setRepeat('NONE')
      setEndDate('')
      return
    }
    const current = series.data?.find((s) => s.id === occurrence?.recurringId)
    setRepeat(current?.interval ?? 'MONTHLY')
    setEndDate(current?.endDate ?? '')
  }

  function validate(): Errors {
    const found: Errors = {}
    const parsed = parseAmountInput(amount)
    if (amount.trim() === '') {
      found.amount = 'Der Betrag ist Pflicht.'
    } else if (parsed === null) {
      found.amount = 'Bitte einen Betrag wie 12,50 eingeben (höchstens 2 Nachkommastellen).'
    } else if (parsed <= 0) {
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
    return found
  }

  function save(): Promise<unknown> {
    const parsed = parseAmountInput(amount) ?? 0
    const common = {
      amount: kind === 'expense' ? -parsed : parsed,
      description: description.trim(),
      ...(accountId === '' ? {} : { accountId: Number(accountId) }),
    }
    const single = transfer ? { ...common, date, transfer: true } : { ...common, categoryId: Number(categoryId), date }
    if (recurring) {
      const input = {
        ...common,
        categoryId: Number(categoryId),
        interval: repeat,
        startDate: date,
        endDate: endDate === '' ? null : endDate,
      }
      return occurrence
        ? updateFollowing.mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.occurrenceDate, input })
        : createSeries.mutateAsync(input)
    }
    if (occurrence) {
      return updateThis.mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.occurrenceDate, input: single })
    }
    if (entry?.id != null) {
      return updateTransaction.mutateAsync({ id: entry.id, input: single })
    }
    return createTransaction.mutateAsync(single)
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
    void run(save, entry ? 'Änderung gespeichert.' : 'Buchung gespeichert.')
  }

  function handleDelete() {
    if (occurrence) {
      void run(
        () => deleteOccurrence.mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.occurrenceDate, scope }),
        'Buchung gelöscht.',
      )
    } else if (entry?.id != null) {
      const id = entry.id
      void run(() => deleteTransaction.mutateAsync(id), 'Buchung gelöscht.')
    }
  }

  const title = !entry ? 'Neue Buchung' : occurrence ? 'Wiederkehrende Buchung bearbeiten' : 'Buchung bearbeiten'
  const intervals = Object.keys(INTERVAL_LABELS) as RecurringInterval[]

  return (
    <Dialog title={title} onClose={onClose}>
      <form className="booking-form" noValidate onSubmit={handleSubmit}>
        <Segmented label="Art" options={KINDS} value={kind} onChange={setKind} className="booking-kind" />

        <div className={errors.amount ? 'amount-field invalid' : 'amount-field'}>
          <span className={kind === 'income' ? 'amount-sign positive' : 'amount-sign'} aria-hidden="true">
            {kind === 'income' ? '+' : '−'}
          </span>
          <input
            className="amount-input"
            inputMode="decimal"
            placeholder="0,00"
            aria-label="Betrag in Euro"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            aria-invalid={errors.amount ? true : undefined}
            aria-describedby={errors.amount ? 'booking-amount-error' : undefined}
          />
          <span className="amount-sign" aria-hidden="true">
            €
          </span>
        </div>
        {errors.amount && (
          <span className="error" id="booking-amount-error">
            {errors.amount}
          </span>
        )}

        <label className="field">
          Beschreibung
          <input
            value={description}
            maxLength={100}
            placeholder="z. B. Supermarkt"
            onChange={(e) => setDescription(e.target.value)}
            aria-invalid={errors.description ? true : undefined}
          />
          {errors.description && <span className="error">{errors.description}</span>}
        </label>

        <div className="field">
          <span className="field-label">
            <span id="booking-category-label">Kategorie</span>
            {suggested !== null && <span className="badge suggestion">Vorschlag</span>}
          </span>
          {quick.length > 0 && (
            <div className="chips" role="group" aria-label="Zuletzt genutzt">
              {quick.map((category) => (
                <button
                  key={category.id}
                  type="button"
                  className="chip"
                  aria-pressed={String(category.id) === categoryId}
                  onClick={() => chooseCategory(String(category.id))}
                >
                  <CategoryIcon icon={category.icon} size={14} />
                  {category.name}
                </button>
              ))}
            </div>
          )}
          <select
            aria-labelledby="booking-category-label"
            value={categoryId}
            onChange={(e) => chooseCategory(e.target.value)}
            aria-invalid={errors.categoryId ? true : undefined}
          >
            <option value="">Kategorie wählen …</option>
            {!occurrence && <option value={TRANSFER}>Umbuchung zwischen eigenen Konten (zählt nicht)</option>}
            {options.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {errors.categoryId && <span className="error">{errors.categoryId}</span>}
        </div>

        {occurrence && (
          <fieldset className="field scope-choice">
            <legend>Änderung gilt für</legend>
            <label>
              <input type="radio" name="scope" checked={scope === 'THIS'} onChange={() => chooseScope('THIS')} />
              Nur dieser Eintrag
            </label>
            <label>
              <input
                type="radio"
                name="scope"
                checked={scope === 'FOLLOWING'}
                // Waits for the series, which fills interval and end date
                disabled={!series.data}
                onChange={() => chooseScope('FOLLOWING')}
              />
              Dieser und alle folgenden
            </label>
            <p className="hint">
              Eintrag vom {formatDate(occurrence.occurrenceDate)}; frühere Einträge bleiben unverändert.
            </p>
          </fieldset>
        )}

        <div className="field-row">
          <label className="field">
            {recurring ? 'Erste Abbuchung' : 'Datum'}
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-invalid={errors.date ? true : undefined}
            />
            {errors.date && <span className="error">{errors.date}</span>}
          </label>

          {repeatOption !== 'none' && (
            <label className="field">
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
        </div>

        {recurring && (
          <label className="field">
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

        {accountOptions.length > 1 && (
          <label className="field">
            Konto
            <select
              value={accountId === '' ? String(defaultAccount?.id ?? '') : accountId}
              onChange={(e) => setAccountId(e.target.value)}
              aria-invalid={errors.accountId ? true : undefined}
            >
              {accountOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
            {errors.accountId && <span className="error">{errors.accountId}</span>}
          </label>
        )}

        {generalError && (
          <p className="error" role="alert">
            {generalError}
          </p>
        )}

        {confirmingDelete ? (
          <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
            <span>
              {occurrence
                ? scope === 'THIS'
                  ? 'Nur diesen Eintrag löschen?'
                  : 'Diesen und alle folgenden Einträge löschen?'
                : 'Buchung wirklich löschen?'}
            </span>
            <div className="dialog-buttons">
              <button type="button" onClick={() => setConfirmingDelete(false)} disabled={submitting}>
                Zurück
              </button>
              <button type="button" className="danger" onClick={handleDelete} disabled={submitting}>
                Endgültig löschen
              </button>
            </div>
          </div>
        ) : (
          <div className="dialog-actions">
            <div>
              {entry && (
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
