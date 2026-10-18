import { useState } from 'react'
import { useCategories } from '../api/categories'
import type { YearlyTransaction } from '../api/types'
import {
  useCreateYearlyTransaction,
  useDeleteYearlyTransaction,
  useUpdateYearlyTransaction,
  useYearlyTransactions,
} from '../api/yearlyTransactions'
import { YearlyTransactionForm } from '../components/YearlyTransactionForm'
import { formatDate, formatEuro, formatMonth } from '../format'

export function YearlyTransactionsPage({ month }: { month: string }) {
  const transactions = useYearlyTransactions(month)
  const categories = useCategories(month)
  const createTransaction = useCreateYearlyTransaction()
  const updateTransaction = useUpdateYearlyTransaction()
  const deleteTransaction = useDeleteYearlyTransaction()
  const [editing, setEditing] = useState<YearlyTransaction | null>(null)

  function handleDelete(transaction: YearlyTransaction) {
    if (window.confirm(`Jährliche Transaktion „${transaction.description}“ löschen?`)) {
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
      <h2>Jährliche Transaktionen</h2>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}
      {categories.data && (
        <YearlyTransactionForm
          // Remount on switch so the fields show the selected entry.
          key={editing?.id ?? `new-${month}`}
          categories={categories.data}
          transaction={editing}
          defaultValidFrom={month}
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

      <h3>Gültig im {formatMonth(month)}</h3>
      {transactions.isPending && <p>Lade jährliche Transaktionen …</p>}
      {transactions.error && (
        <p className="error">Jährliche Transaktionen konnten nicht geladen werden: {transactions.error.message}</p>
      )}
      {transactions.data && transactions.data.length === 0 && (
        <p>Keine jährlichen Transaktionen in diesem Monat gültig.</p>
      )}
      {transactions.data && transactions.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Beschreibung</th>
              <th>Kategorie</th>
              <th className="number">Betrag/Jahr</th>
              <th className="number">Betrag/Monat</th>
              <th>Gültig ab</th>
              <th>Gültig bis</th>
              <th>Abbuchung</th>
              <th>Aktionen</th>
            </tr>
          </thead>
          <tbody>
            {transactions.data.map((t) => (
              <tr key={t.id}>
                <td>{t.description}</td>
                <td>
                  {t.categoryName} ({t.categoryType === 'INCOME' ? 'Einnahme' : 'Ausgabe'})
                </td>
                <td className="number">{formatEuro(t.amountPerYear)}</td>
                <td className="number">{formatEuro(t.amountPerMonth)}</td>
                <td>{formatMonth(t.validFrom)}</td>
                <td>{t.validTo === null ? 'unbefristet' : formatMonth(t.validTo)}</td>
                <td>{t.dueDate === null ? '–' : formatDate(t.dueDate)}</td>
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
