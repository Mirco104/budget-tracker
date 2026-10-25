import { useCategories, useUpdateTarget } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import type { CategoryGroup } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { formatEuro, formatMonth } from '../format'
import { GROUP_LABELS, UNGROUPED_LABEL } from '../labels'

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
          <dt>Nicht verplant</dt>
          <dd className={data.unplanned < 0 ? 'negative' : undefined}>{formatEuro(data.unplanned)}</dd>
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

export function SettingsPage({ month }: { month: string }) {
  const categories = useCategories(month)
  const updateTarget = useUpdateTarget()

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const ungrouped = categories.data.filter((c) => c.group === null)
  const groups = (Object.keys(GROUP_LABELS) as CategoryGroup[]).map((group) => ({
    group,
    items: categories.data.filter((c) => c.group === group),
  }))

  return (
    <section>
      <h2>Einstellungen</h2>
      <p className="hint">
        Soll je Kategorie in Euro oder in Prozent der Einnahmen. Ist beides gesetzt, gilt der Euro-Wert.
      </p>
      <p className="hint">
        Gilt ab <strong>{formatMonth(month)}</strong> für alle folgenden Monate, bis ein späterer Monat ein neues Soll
        bekommt. Frühere Monate bleiben unverändert.
      </p>
      <PlanningSummary month={month} />

      {groups.map(({ group, items }) => (
        <section key={group}>
          <h3>{GROUP_LABELS[group]}</h3>
          {items.map((category) => (
            <TargetRow
              key={`${category.id}-${month}`}
              category={category}
              month={month}
              onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, month, input })}
            />
          ))}
        </section>
      ))}

      <section>
        <h3>{UNGROUPED_LABEL}</h3>
        <p className="hint">Typisch für Einnahmen; ohne Soll.</p>
        <ul>
          {ungrouped.map((category) => (
            <li key={category.id}>{category.name}</li>
          ))}
        </ul>
      </section>
    </section>
  )
}
