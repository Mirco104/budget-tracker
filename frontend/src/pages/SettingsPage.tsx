import { useCategories, useUpdateTarget } from '../api/categories'
import type { CategoryGroup } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { formatMonth } from '../format'
import { GROUP_LABELS } from '../labels'

export function SettingsPage({ month }: { month: string }) {
  const categories = useCategories(month)
  const updateTarget = useUpdateTarget()

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const income = categories.data.filter((c) => c.type === 'INCOME')
  const expenseGroups = (Object.keys(GROUP_LABELS) as CategoryGroup[]).map((group) => ({
    group,
    items: categories.data.filter((c) => c.type === 'EXPENSE' && c.group === group),
  }))

  return (
    <section>
      <h2>Einstellungen</h2>
      <p className="hint">
        Soll je Ausgaben-Kategorie in Euro oder in Prozent der Einnahmen. Ist beides gesetzt, gilt der Euro-Wert.
      </p>
      <p className="hint">
        Gilt ab <strong>{formatMonth(month)}</strong> für alle folgenden Monate, bis ein späterer Monat ein neues Soll
        bekommt. Frühere Monate bleiben unverändert.
      </p>

      {expenseGroups.map(({ group, items }) => (
        <section key={group}>
          <h3>{GROUP_LABELS[group]}</h3>
          {items.map((category) =>
            category.system ? (
              <div key={category.id} className="target-row">
                <span className="target-name">{category.name}</span>
                <span className="hint">wird automatisch berechnet (Einnahmen − übrige Soll-Werte)</span>
              </div>
            ) : (
              <TargetRow
                key={`${category.id}-${month}`}
                category={category}
                month={month}
                onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, month, input })}
              />
            ),
          )}
        </section>
      ))}

      <section>
        <h3>Einnahmen</h3>
        <ul>
          {income.map((category) => (
            <li key={category.id}>{category.name}</li>
          ))}
        </ul>
      </section>
    </section>
  )
}
