import { useEffect, useState, type FormEvent } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { ApiError } from '../api/client'
import { useSavingGoals } from '../api/goals'
import { AccountSelect } from './AccountSelect'
import { CategoryPicker } from './CategoryPicker'
import { usePlan } from '../api/plan'
import { useBookingSuggestion } from '../api/suggestions'
import { useCreateTransaction, useDeleteTransaction, useTransactions, useUpdateTransaction } from '../api/transactions'
import type { Category, TermRef, Transaction } from '../api/types'
import { formatDate, formatEuro, parseAmountInput } from '../format'
import { CategoryIcon } from './ui/CategoryIcon'
import { Dialog } from './ui/Dialog'
import { Segmented } from './ui/Segmented'

type Kind = 'expense' | 'income'
type Field = 'amount' | 'description' | 'date' | 'categoryId' | 'accountId'
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
  /**
   * P7: a new booking that fulfils this term ("Anderer Betrag oder anderes Datum"), prefilled with its values; the
   * booking is linked to the term when it is saved.
   */
  term?: (TermRef & { name: string; amount: number; categoryId: number | null; accountId: number }) | null
}

const KINDS: { value: Kind; label: string }[] = [
  { value: 'expense', label: 'Ausgabe' },
  { value: 'income', label: 'Einnahme' },
]

// A term link that fails is shown at the date (P5)
const SERVER_FIELDS: Record<string, Field> = { replaces: 'date' }

const QUICK_PICKS = 4

/** The value of the category field for a transfer (R27). */
const TRANSFER = 'transfer'
/** The value of the category field for the kind Sparen (P1): a transfer to a savings or depot account. */
const SAVING = 'saving'
/** The value of a field that opens the name field for a new category or account (08.11.2026). */

function toAmountText(amount: number): string {
  return Math.abs(amount).toFixed(2).replace('.', ',')
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
 * Booking dialog (US27): Ausgabe / Einnahme, a large amount, description, category with the last used ones, date and
 * account. Only real bookings (P1); regular payments are planned items on the planning page (section 19.6). Delete is
 * in the dialog and asks once more.
 */
export function BookingDialog({ categories: loaded, entry, defaultDate, onClose, onDone, term = null }: Props) {
  // Already loaded on the transactions page; the overview loads it when the dialog opens
  const recent = useTransactions(defaultDate.slice(0, 7)).data ?? []
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const bankAccounts = useBankAccounts().data ?? []
  // P8: the pots of the rare items of the current month and the saving goals offer "aus Rücklage bezahlen"
  const pots = (usePlan(defaultDate.slice(0, 7)).data?.items ?? []).filter(
    (item) => item.rhythmUnit === 'MONTH' && (item.rhythmCount ?? 1) > 1 && item.group === 'FIXED',
  )
  const goals = useSavingGoals().data ?? []
  // A category created here, until the categories of the page contain it (for the quick chips)
  const [created, setCreated] = useState<Category[]>([])
  const categories = [...loaded, ...created.filter((c) => !loaded.some((l) => l.id === c.id))]

  // The amount is entered positive; the switch sets the sign (R11).
  const initialAmount = entry?.amount ?? term?.amount
  const [kind, setKind] = useState<Kind>(initialAmount !== undefined && initialAmount > 0 ? 'income' : 'expense')
  const [amount, setAmount] = useState(initialAmount !== undefined ? toAmountText(initialAmount) : '')
  const [description, setDescription] = useState(entry?.description ?? term?.name ?? '')
  // TRANSFER stands for a transfer between own accounts, which has no category (R27)
  const [categoryId, setCategoryId] = useState(
    entry
      ? entry.saving
        ? SAVING
        : entry.transfer
          ? TRANSFER
          : String(entry.categoryId)
      : term?.categoryId
        ? String(term.categoryId)
        : '',
  )
  // '' is the default account (R26)
  const [accountId, setAccountId] = useState(entry ? String(entry.accountId) : term ? String(term.accountId) : '')
  const [date, setDate] = useState(entry?.date ?? term?.date ?? defaultDate)
  // 27f4 (decision 60): a new booking for the term of a rare item is paid from its reserve unless unticked
  const termPot = term && !entry ? pots.find((pot) => pot.id === term.itemId) : undefined
  // "pot:<item key>" or "goal:<id>"; '' pays from the monthly budget. reserveItemKey of an entry is also set when it
  // only fulfils the term of the rare item; null until the user chooses, then the term decides
  const [chosenPaidFrom, setPaidFrom] = useState<string | null>(
    entry?.reserveItemKey ? `pot:${entry.reserveItemKey}` : entry?.savingGoalId ? `goal:${entry.savingGoalId}` : null,
  )
  const paidFrom = chosenPaidFrom ?? (termPot ? `pot:${termPot.itemKey}` : '')
  const [chosenFromReserve, setFromReserve] = useState<boolean | null>(entry ? chosenPaidFrom !== null : null)
  const fromReserve = chosenFromReserve ?? termPot !== undefined
  const wasFromReserve = entry ? entry.reserveItemKey != null || entry.savingGoalId != null : termPot !== undefined
  // 27f3: the target account of a new transfer; '' = not in the app (only one booking)
  const [targetAccountId, setTargetAccountId] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const quick = lastUsed(recent, categories, kind)
  // US32: the suggestion for the description, asked for once typing pauses
  const asked = useDebounced(description.trim(), 300)
  const suggestion = useBookingSuggestion(asked, kind === 'income', categoryId === '' && asked.length >= 2)
  // US32, decision 46: the suggestion is never preset; it is offered as a button while no category is chosen
  const suggestedId = suggestion.data?.categoryId
  const suggested =
    categoryId === '' && suggestedId != null ? categories.find((c) => c.id === suggestedId && !c.archived) : undefined

  const saving = categoryId === SAVING
  const transfer = categoryId === TRANSFER || saving

  function chooseCategory(value: string) {
    setCategoryId(value)
  }

  // 27f4: choosing a reserve suggests its linked account; the user may set the everyday account back
  function choosePaidFrom(value: string) {
    setPaidFrom(value)
    const linked = pots.find((pot) => `pot:${pot.itemKey}` === value)?.reserveAccountId
    if (linked != null) {
      setAccountId(String(linked))
    }
  }

  // The field shows once the accounts are loaded, so a second account can be created here (08.11.2026)
  const defaultAccount = bankAccounts.find((a) => a.defaultAccount)
  const sourceAccount = accountId === '' ? String(defaultAccount?.id ?? '') : accountId
  const reserveAccountId = pots.find((pot) => `pot:${pot.itemKey}` === paidFrom)?.reserveAccountId
  const reserveAccount = bankAccounts.find((a) => a.id === reserveAccountId)

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
    const linked = term && !entry && !transfer ? { replaces: { itemId: term.itemId, date: term.date } } : {}
    const reserve =
      fromReserve && kind === 'expense' && paidFrom.startsWith('pot:')
        ? { reserveItemKey: paidFrom.slice(4) }
        : fromReserve && kind === 'expense' && paidFrom.startsWith('goal:')
          ? { savingGoalId: Number(paidFrom.slice(5)) }
          : // 27f4: unticked, an expense that fulfils the term of a rare item counts fully in the budget
            !fromReserve && kind === 'expense' && wasFromReserve
            ? { noReserve: true }
            : {}
    const target = !entry && targetAccountId !== '' ? { targetAccountId: Number(targetAccountId) } : {}
    const single = transfer
      ? { ...common, date, transfer: true, ...(saving ? { saving: true } : {}), ...target }
      : { ...common, categoryId: Number(categoryId), date, ...linked, ...reserve }
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
    if (entry?.id != null) {
      const id = entry.id
      void run(() => deleteTransaction.mutateAsync(id), 'Buchung gelöscht.')
    }
  }

  const title = entry ? 'Buchung bearbeiten' : term ? 'Als Buchung eintragen' : 'Neue Buchung'

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
          <span id="booking-category-label">Kategorie</span>
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
                  {category.path}
                </button>
              ))}
            </div>
          )}
          <CategoryPicker
            labelledBy="booking-category-label"
            value={categoryId}
            onChange={chooseCategory}
            categories={categories}
            placeholder="Kategorie wählen …"
            invalid={errors.categoryId !== undefined}
            onCreated={(category) => setCreated((current) => [...current, category])}
            leading={
              !term && (
                <>
                  <option value={TRANSFER}>Umbuchung zwischen eigenen Konten (zählt nicht)</option>
                  <option value={SAVING}>Sparen (auf ein Spar- oder Depotkonto)</option>
                </>
              )
            }
          />
          {suggested && (
            <button type="button" className="chip suggestion-chip" onClick={() => chooseCategory(String(suggested.id))}>
              <CategoryIcon icon={suggested.icon} size={14} />
              Vorschlag: {suggested.path}
            </button>
          )}
          {errors.categoryId && <span className="error">{errors.categoryId}</span>}
        </div>

        <label className="field">
          Datum
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            aria-invalid={errors.date ? true : undefined}
          />
          {errors.date && <span className="error">{errors.date}</span>}
        </label>

        {!transfer && kind === 'expense' && (pots.length > 0 || goals.length > 0) && (
          <div className="field">
            <label className="check-field">
              <input
                type="checkbox"
                checked={fromReserve}
                onChange={(e) => {
                  setFromReserve(e.target.checked)
                  if (e.target.checked && paidFrom === '') {
                    choosePaidFrom(pots[0] ? `pot:${pots[0].itemKey}` : `goal:${goals[0].id}`)
                  }
                }}
              />
              aus Rücklage / Ersparnissen bezahlen
            </label>
            {fromReserve && (
              <select aria-label="Bezahlt aus" value={paidFrom} onChange={(e) => choosePaidFrom(e.target.value)}>
                {pots.map((pot) => (
                  <option key={pot.itemKey} value={`pot:${pot.itemKey}`}>
                    Rücklage: {pot.name}
                  </option>
                ))}
                {goals.map((goal) => (
                  <option key={goal.id} value={`goal:${goal.id}`}>
                    Sparziel: {goal.name}
                  </option>
                ))}
              </select>
            )}
            {fromReserve && <span className="hint">Die Ausgabe belastet nicht das Monatsbudget.</span>}
            {fromReserve && reserveAccount && String(reserveAccount.id) !== sourceAccount && (
              <span className="hint">
                Die Rücklage liegt auf {reserveAccount.name}. Damit die Kontostände stimmen, trage die Umbuchung von{' '}
                {reserveAccount.name} auf dieses Konto extra ein.
              </span>
            )}
          </div>
        )}

        {term && (
          <p className="hint">
            Erfüllt den geplanten Termin „{term.name}“ vom {formatDate(term.date)} ({formatEuro(Math.abs(term.amount))}).
          </p>
        )}

        {bankAccounts.length > 0 && (
          <div className="field">
            <span id="booking-account-label">{transfer && !entry ? 'Von Konto' : 'Konto'}</span>
            <AccountSelect
              labelledBy="booking-account-label"
              value={accountId}
              onChange={setAccountId}
              invalid={errors.accountId !== undefined}
            />
            {errors.accountId && <span className="error">{errors.accountId}</span>}
          </div>
        )}

        {/* 27f3: a new transfer names its target; then both bookings are made, edited and deleted together */}
        {transfer && !entry && bankAccounts.length > 0 && (
          <div className="field">
            <span id="booking-target-label">Auf Konto</span>
            <AccountSelect
              labelledBy="booking-target-label"
              value={targetAccountId}
              onChange={setTargetAccountId}
              empty="nicht in der App"
              exclude={sourceAccount}
            />
          </div>
        )}
        {entry?.transferPeerAccountName && (
          <p className="hint">
            Gegenbuchung auf {entry.transferPeerAccountName}: Betrag, Beschreibung und Löschen gelten für beide.
          </p>
        )}

        {generalError && (
          <p className="error" role="alert">
            {generalError}
          </p>
        )}

        {confirmingDelete ? (
          <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
            <span>
              {entry?.auto
                ? 'Automatischen Eintrag löschen? Er wird für diesen Termin nicht neu angelegt.'
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
