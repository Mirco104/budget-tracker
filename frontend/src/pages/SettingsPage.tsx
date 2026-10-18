import { useCategories, useUpdateTarget } from '../api/categories'
import type { CategoryGroup } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { GROUP_LABELS } from '../labels'

export function SettingsPage() {
  const categories = useCategories()
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
                key={category.id}
                category={category}
                onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, input })}
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
