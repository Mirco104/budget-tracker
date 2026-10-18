import type { Category, CategoryGroup } from '../api/types'
import { GROUP_LABELS } from '../labels'

interface Props {
  categories: Category[]
  /** Selected category id as a string, '' for none. */
  value: string
  onChange: (value: string) => void
  error?: string
}

/** Category dropdown, grouped into income and the expense groups. */
export function CategorySelect({ categories, value, onChange, error }: Props) {
  const income = categories.filter((c) => c.type === 'INCOME')
  const expenseGroups = (Object.keys(GROUP_LABELS) as CategoryGroup[]).map((group) => ({
    group,
    items: categories.filter((c) => c.type === 'EXPENSE' && c.group === group),
  }))

  return (
    <label>
      Kategorie
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-invalid={error ? true : undefined}>
        <option value="">Bitte wählen</option>
        <optgroup label="Einnahmen">
          {income.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </optgroup>
        {expenseGroups.map(({ group, items }) => (
          <optgroup key={group} label={GROUP_LABELS[group]}>
            {items.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
      {error && <span className="error">{error}</span>}
    </label>
  )
}
