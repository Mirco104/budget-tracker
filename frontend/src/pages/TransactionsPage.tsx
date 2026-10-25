import { useState } from 'react'
import { useCategories } from '../api/categories'
import {
  useDeleteOccurrence,
  useRecurringTransactions,
  useUpdateOccurrenceFollowing,
  useUpdateOccurrenceThis,
} from '../api/recurring'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from '../api/transactions'
import type { OccurrenceScope, RecurringTransactionInput, Transaction } from '../api/types'
import { OccurrenceScopeDialog } from '../components/OccurrenceScopeDialog'
import { RecurringTransactionForm } from '../components/RecurringTransactionForm'
import { TransactionForm } from '../components/TransactionForm'
import { formatDate, formatEuro } from '../format'

function defaultDateFor(month: string): string {
  const today = new Date()
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return todayIso.startsWith(month) ? todayIso : `${month}-01`
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
  | { mode: 'following'; occurrence: OccurrenceRef; initial: RecurringTransactionInput }

export function TransactionsPage({ month }: { month: string }) {
  const transactions = useTransactions(month)
  const categories = useCategories(month)
  const series = useRecurringTransactions(month)
  const createTransaction = useCreateTransaction()
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
            categoryId: current.categoryId,
            interval: current.interval,
            startDate: occurrence.date,
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
        <RecurringTransactionForm
          key={`following-${occurrence.recurringId}-${occurrence.date}`}
          categories={categories.data}
          initial={initial}
          defaultStartDate={occurrence.date}
          title={`„${occurrence.entry.description}“ ab ${formatDate(occurrence.date)} bearbeiten`}
          submitLabel="Speichern"
          onSubmit={(input) =>
            updateOccurrenceFollowing
              .mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.date, input })
              .then(() => setEditing(null))
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
          transaction={occurrence.entry}
          defaultDate={occurrence.date}
          title={`Nur den Eintrag vom ${formatDate(occurrence.date)} bearbeiten`}
          onSubmit={(input) =>
            updateOccurrenceThis
              .mutateAsync({ recurringId: occurrence.recurringId, date: occurrence.date, input })
              .then(() => setEditing(null))
          }
          onCancel={() => setEditing(null)}
        />
      )
    }
    const single = editing?.mode === 'single' ? editing.transaction : null
    return (
      <TransactionForm
        // Remount on switch so the fields show the selected transaction.
        key={single ? `single-${single.id}` : `new-${month}`}
        categories={categories.data}
        transaction={single}
        defaultDate={defaultDateFor(month)}
        onSubmit={(input) =>
          single?.id != null
            ? updateTransaction.mutateAsync({ id: single.id, input }).then(() => setEditing(null))
            : createTransaction.mutateAsync(input)
        }
        onCancel={single ? () => setEditing(null) : undefined}
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

      {transactions.isPending && <p>Lade Transaktionen …</p>}
      {transactions.error && (
        <p className="error">Transaktionen konnten nicht geladen werden: {transactions.error.message}</p>
      )}
      {transactions.data && transactions.data.length === 0 && <p>Keine Transaktionen in diesem Monat.</p>}
      {transactions.data && transactions.data.length > 0 && (
        <table>
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
                <td>{formatDate(t.date)}</td>
                <td>
                  {t.description}
                  {t.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
                </td>
                <td>
                  {t.categoryName} ({t.amount < 0 ? 'Ausgabe' : 'Einnahme'})
                </td>
                <td className="number">{formatEuro(t.amount)}</td>
                <td>
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
