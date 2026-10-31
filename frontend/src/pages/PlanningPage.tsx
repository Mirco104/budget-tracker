import { useCategories, useUpdateTarget } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import { TargetRow } from '../components/TargetRow'
import { formatEuro, formatMonth } from '../format'

/** Income of the month, planned targets and the unplanned rest (R4), taken from the dashboard. */
function PlanningSummary({ month }: { month: string }) {
  const { data } = useDashboard(month)
  if (!data) {
    return null
  }
  return (
    <>
      <dl className="summary">
        <div>
          <dt>Einnahmen {formatMonth(month)}</dt>
          <dd>{formatEuro(data.incomeTotal)}</dd>
        </div>
        <div>
          <dt>Verplant</dt>
          <dd>{formatEuro(data.expenses.target)}</dd>
        </div>
        <div>
          <dt>{data.unplannedLabel}</dt>
          {/* The label already says "Überplant", so the amount is shown without minus sign (R4). */}
          <dd className={data.unplanned < 0 ? 'negative' : undefined}>{formatEuro(Math.abs(data.unplanned))}</dd>
        </div>
      </dl>
      {data.incomeTotal === 0 && (
        <p className="hint">
          Für diesen Monat sind noch keine Einnahmen gebucht. Prozent-Soll ergeben daher 0,00 €.
        </p>
      )}
    </>
  )
}

export function PlanningPage({ month }: { month: string }) {
  const categories = useCategories(month)
  const updateTarget = useUpdateTarget()

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  return (
    <section>
      <h2>Planung</h2>
      <p className="hint">
        Soll je Kategorie in Euro oder in Prozent der Einnahmen. Ist beides gesetzt, gilt der Euro-Wert. Änderungen
        werden automatisch gespeichert, sobald du ein Feld verlässt oder Enter drückst.
      </p>
      <p className="hint">
        Gilt ab <strong>{formatMonth(month)}</strong> für alle folgenden Monate, bis ein späterer Monat ein neues Soll
        bekommt. Frühere Monate bleiben unverändert.
      </p>
      <PlanningSummary month={month} />

      <h3>Kategorien</h3>
      {categories.data.filter((category) => !category.archived).map((category) => (
        <TargetRow
          key={`${category.id}-${month}`}
          category={category}
          month={month}
          onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, month, input })}
        />
      ))}
    </section>
  )
}
