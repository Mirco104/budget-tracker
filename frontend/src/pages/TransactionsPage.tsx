import { useState } from 'react'
import { useCategories } from '../api/categories'
import {
  useCreateRecurringTransaction,
  useDeleteOccurrence,
  useRecurringTransactions,
  useUpdateOccurrenceFollowing,
  useUpdateOccurrenceThis,
} from '../api/recurring'
import { useReserves } from '../api/reserves'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from '../api/transactions'
import type { OccurrenceScope, Transaction } from '../api/types'
import { OccurrenceScopeDialog } from '../components/OccurrenceScopeDialog'
import { TransactionForm, type TransactionFormValues } from '../components/TransactionForm'
import { formatDate, formatEuro } from '../format'

function defaultDateFor(month: string): string {
  const today = new Date()
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return todayIso.startsWith(month) ? todayIso : `${month}-01`
}

function valuesOf(t: Transaction): TransactionFormValues {
  return { amount: t.amount, description: t.description, date: t.date, categoryId: t.categoryId, repeat: 'NONE', endDate: null }
}

function rowKey(t: Transaction): string {
  return t.kind === 'SINGLE' ? `single-${t.id}` : `recurring-${t.recurringId}-${t.occurrenceDate}`
}

/** An occurrence of a recurring transaction; recurringId and occurrenceDate are set (kind RECURRING). */
interface OccurrenceRef {
  entry: Transaction
  recurringId: number
  date: string
}

type Editing =
  | { mode: 'single'; transaction: Transaction }
  | { mode: 'this'; occurrence: OccurrenceRef }
  | { mode: 'following'; occurrence: OccurrenceRef; initial: TransactionFormValues }

export function TransactionsPage({ month }: { month: string }) {
  const transactions = useTransactions(month)
  const categories = useCategories(month)
  // Series active in the month; "Dieser und alle folgenden" prefills the form from them.
  const series = useRecurringTransactions(month)
  // Compact reserve pot (R14), because series are maintained here; details on the Auswertung page.
  const reserves = useReserves(month)
  const createTransaction = useCreateTransaction()
  const createSeries = useCreateRecurringTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const updateOccurrenceThis = useUpdateOccurrenceThis()
  const updateOccurrenceFollowing = useUpdateOccurrenceFollowing()
  const deleteOccurrence = useDeleteOccurrence()
  const [editing, setEditing] = useState<Editing | null>(null)
  const [asking, setAsking] = useState<{ action: 'edit' | 'delete'; occurrence: OccurrenceRef } | null>(null)

  function occurrenceOf(t: Transaction): OccurrenceRef | null {
    return t.kind === 'RECURRING' && t.recurringId !== null && t.occurrenceDate !== null
      ? { entry: t, recurringId: t.recurringId, date: t.occurrenceDate }
      : null
  }

  function handleEdit(t: Transaction) {
    const occurrence = occurrenceOf(t)
    if (occurrence) {
      setEditing(null)
      setAsking({ action: 'edit', occurrence })
    } else {
      setAsking(null)
      setEditing({ mode: 'single', transaction: t })
    }
  }

  function handleDelete(t: Transaction) {
    const occurrence = occurrenceOf(t)
    if (occurrence) {
      setAsking({ action: 'delete', occurrence })
    } else if (t.id !== null && window.confirm(`Transaktion „${t.description}“ löschen?`)) {
      const id = t.id
      deleteTransaction.mutate(id, {
        onSuccess: () => {
          if (editing?.mode === 'single' && editing.transaction.id === id) {
            setEditing(null)
          }
        },
      })
    }
  }

  function handleScope(scope: OccurrenceScope) {
    if (!asking) {
      return
    }
    const { action, occurrence } = asking
    setAsking(null)
    if (action === 'delete') {
      deleteOccurrence.mutate(
        { recurringId: occurrence.recurringId, date: occurrence.date, scope },
        { onSuccess: () => setEditing(null) },
      )
    } else if (scope === 'THIS') {
      setEditing({ mode: 'this', occurrence })
    } else {
      // Prefill with the series from this occurrence on; the series is active in the month of its occurrence.
      const current = series.data?.find((s) => s.id === occurrence.recurringId)
      if (current) {
        setEditing({
          mode: 'following',
          occurrence,
          initial: {
            amount: current.amount,
            description: current.description,
            date: occurrence.date,
            categoryId: current.categoryId,
            repeat: current.interval,
            endDate: current.endDate,
          },
        })
      }
    }
  }

  function renderForm() {
    if (!categories.data) {
      return null
    }
    if (editing?.mode === 'following') {
      const { occurrence, initial } = editing
      return (
        <TransactionForm
          key={`following-${occurrence.recurringId}-${occurrence.date}`}
          categories={categories.data}
          initial={initial}
          defaultDate={occurrence.date}
          repeatOption="required"
          title={`„${occurrence.entry.description}“ ab ${formatDate(occurrence.date)} bearbeiten`}
          submitLabel="Speichern"
          onSubmit={(result) =>
            result.kind === 'recurring'
              ? updateOccurrenceFollowing
                  .mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.date, input: result.input })
                  .then(() => setEditing(null))
              : Promise.reject(new Error('Eine Serie braucht ein Intervall.'))
          }
          onCancel={() => setEditing(null)}
        />
      )
    }
    if (editing?.mode === 'this') {
      const { occurrence } = editing
      return (
        <TransactionForm
          key={`this-${occurrence.recurringId}-${occurrence.date}`}
          categories={categories.data}
          initial={valuesOf(occurrence.entry)}
          defaultDate={occurrence.date}
          repeatOption="none"
          title={`Nur den Eintrag vom ${formatDate(occurrence.date)} bearbeiten`}
          submitLabel="Speichern"
          onSubmit={(result) =>
            result.kind === 'single'
              ? updateOccurrenceThis
                  .mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.date, input: result.input })
                  .then(() => setEditing(null))
              : Promise.reject(new Error('Ein einzelner Eintrag hat kein Intervall.'))
          }
          onCancel={() => setEditing(null)}
        />
      )
    }
    if (editing?.mode === 'single') {
      const single = editing.transaction
      return (
        <TransactionForm
          // Remount on switch so the fields show the selected transaction.
          key={`single-${single.id}`}
          categories={categories.data}
          initial={valuesOf(single)}
          defaultDate={defaultDateFor(month)}
          repeatOption="none"
          title="Transaktion bearbeiten"
          submitLabel="Speichern"
          onSubmit={(result) =>
            single.id !== null && result.kind === 'single'
              ? updateTransaction.mutateAsync({ id: single.id, input: result.input }).then(() => setEditing(null))
              : Promise.reject(new Error('Speichern fehlgeschlagen.'))
          }
          onCancel={() => setEditing(null)}
        />
      )
    }
    // US6: one form for single and recurring transactions; "Wiederholen" picks the endpoint.
    return (
      <TransactionForm
        key={`new-${month}`}
        categories={categories.data}
        initial={null}
        defaultDate={defaultDateFor(month)}
        repeatOption="optional"
        title="Neue Transaktion"
        submitLabel="Hinzufügen"
        onSubmit={(result) =>
          result.kind === 'recurring' ? createSeries.mutateAsync(result.input) : createTransaction.mutateAsync(result.input)
        }
      />
    )
  }

  const deleteError = deleteTransaction.error ?? deleteOccurrence.error

  return (
    <section>
      <h2>Transaktionen</h2>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}
      {renderForm()}

      {asking && (
        <OccurrenceScopeDialog
          title={`„${asking.occurrence.entry.description}“ vom ${formatDate(asking.occurrence.date)} ${
            asking.action === 'edit' ? 'bearbeiten' : 'löschen'
          }`}
          onChoose={handleScope}
          onCancel={() => setAsking(null)}
        />
      )}

      {deleteError && <p className="error">Löschen fehlgeschlagen: {deleteError.message}</p>}

      {reserves.data && reserves.data.reserves.length > 0 && (
        <p className="hint">
          Rücklagen: monatlich {formatEuro(reserves.data.monthlyReserve)} zurücklegen, jetzt angespart{' '}
          {formatEuro(reserves.data.reserveBalance)}.
        </p>
      )}

      {transactions.isPending && <p>Lade Transaktionen …</p>}
      {transactions.error && (
        <p className="error">Transaktionen konnten nicht geladen werden: {transactions.error.message}</p>
      )}
      {transactions.data && transactions.data.length === 0 && <p>Keine Transaktionen in diesem Monat.</p>}
      {transactions.data && transactions.data.length > 0 && (
        <table className="transactions">
          <thead>
            <tr>
              <th>Datum</th>
              <th>Beschreibung</th>
              <th>Kategorie</th>
              <th className="number">Betrag</th>
              <th>Aktionen</th>
            </tr>
          </thead>
          <tbody>
            {transactions.data.map((t) => (
              <tr key={rowKey(t)}>
                <td className="tx-date">{formatDate(t.date)}</td>
                <td className="tx-description">
                  {t.description}
                  {t.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
                </td>
                <td className="tx-category">{t.categoryName}</td>
                <td className={`number tx-amount ${t.amount < 0 ? 'negative' : 'positive'}`}>{formatEuro(t.amount)}</td>
                <td className="tx-actions">
                  <button type="button" onClick={() => handleEdit(t)}>
                    Bearbeiten
                  </button>{' '}
                  <button
                    type="button"
                    onClick={() => handleDelete(t)}
                    disabled={deleteTransaction.isPending || deleteOccurrence.isPending}
                  >
                    Löschen
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
