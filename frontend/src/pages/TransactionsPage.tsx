import { useState } from 'react'
import { useCategories } from '../api/categories'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from '../api/transactions'
import type { Transaction } from '../api/types'
import { TransactionForm } from '../components/TransactionForm'
import { formatDate, formatEuro } from '../format'

function defaultDateFor(month: string): string {
  const today = new Date()
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return todayIso.startsWith(month) ? todayIso : `${month}-01`
}

export function TransactionsPage({ month }: { month: string }) {
  const transactions = useTransactions(month)
  const categories = useCategories(month)
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const [editing, setEditing] = useState<Transaction | null>(null)

  function handleDelete(transaction: Transaction) {
    if (window.confirm(`Transaktion „${transaction.description}“ löschen?`)) {
      deleteTransaction.mutate(transaction.id, {
        onSuccess: () => {
          if (editing?.id === transaction.id) {
            setEditing(null)
          }
        },
      })
    }
  }

  return (
    <section>
      <h2>Transaktionen</h2>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}
      {categories.data && (
        <TransactionForm
          // Remount on switch so the fields show the selected transaction.
          key={editing?.id ?? `new-${month}`}
          categories={categories.data}
          transaction={editing}
          defaultDate={defaultDateFor(month)}
          onSubmit={(input) =>
            editing
              ? updateTransaction.mutateAsync({ id: editing.id, input }).then(() => setEditing(null))
              : createTransaction.mutateAsync(input)
          }
          onCancel={editing ? () => setEditing(null) : undefined}
        />
      )}

      {deleteTransaction.error && (
        <p className="error">Löschen fehlgeschlagen: {deleteTransaction.error.message}</p>
      )}

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
              <tr key={t.id}>
                <td>{formatDate(t.date)}</td>
                <td>{t.description}</td>
                <td>
                  {t.categoryName} ({t.categoryType === 'INCOME' ? 'Einnahme' : 'Ausgabe'})
                </td>
                <td className="number">{formatEuro(t.amount)}</td>
                <td>
                  <button type="button" onClick={() => setEditing(t)}>
                    Bearbeiten
                  </button>{' '}
                  <button type="button" onClick={() => handleDelete(t)} disabled={deleteTransaction.isPending}>
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
