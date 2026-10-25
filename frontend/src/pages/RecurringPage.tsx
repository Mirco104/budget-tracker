import { useCategories } from '../api/categories'
import { useCreateRecurringTransaction, useRecurringTransactions } from '../api/recurring'
import { RecurringTransactionForm } from '../components/RecurringTransactionForm'
import { formatDate, formatEuro } from '../format'
import { INTERVAL_LABELS } from '../labels'

/** Create series and list the ones active in the month (US6). Occurrences are edited on the transactions page. */
export function RecurringPage({ month }: { month: string }) {
  const categories = useCategories(month)
  const series = useRecurringTransactions(month)
  const createSeries = useCreateRecurringTransaction()

  return (
    <section>
      <h2>Wiederkehrende Transaktionen</h2>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}
      {categories.data && (
        <RecurringTransactionForm
          key={`new-${month}`}
          categories={categories.data}
          initial={null}
          defaultStartDate={`${month}-01`}
          title="Neue wiederkehrende Transaktion"
          submitLabel="Hinzufügen"
          onSubmit={(input) => createSeries.mutateAsync(input)}
        />
      )}

      <h3>Aktiv in diesem Monat</h3>
      <p className="hint">
        Einzelne Einträge lassen sich auf der Seite Transaktionen bearbeiten oder löschen („Nur dieser Eintrag“ oder
        „Dieser und alle folgenden“).
      </p>
      {series.isPending && <p>Lade wiederkehrende Transaktionen …</p>}
      {series.error && (
        <p className="error">Wiederkehrende Transaktionen konnten nicht geladen werden: {series.error.message}</p>
      )}
      {series.data && series.data.length === 0 && <p>Keine wiederkehrenden Transaktionen in diesem Monat.</p>}
      {series.data && series.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Beschreibung</th>
              <th>Kategorie</th>
              <th>Intervall</th>
              <th className="number">Betrag</th>
              <th>Start</th>
              <th>Ende</th>
              <th className="number">Ø pro Monat</th>
            </tr>
          </thead>
          <tbody>
            {series.data.map((s) => (
              <tr key={s.id}>
                <td>{s.description}</td>
                <td>
                  {s.categoryName} ({s.amount < 0 ? 'Ausgabe' : 'Einnahme'})
                </td>
                <td>{INTERVAL_LABELS[s.interval]}</td>
                <td className="number">{formatEuro(s.amount)}</td>
                <td>{formatDate(s.startDate)}</td>
                <td>{s.endDate ? formatDate(s.endDate) : 'unbefristet'}</td>
                <td className="number">{formatEuro(s.monthlyEquivalent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
