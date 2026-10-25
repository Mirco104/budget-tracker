import type { Category, CategoryGroup } from '../api/types'
import { GROUP_LABELS } from '../labels'

interface Props {
  categories: Category[]
  /** Selected category id as a string, '' for none. */
  value: string
  onChange: (value: string) => void
  error?: string
}

/** Category dropdown, grouped like the settings. The remainder category is not bookable and is left out. */
export function CategorySelect({ categories, value, onChange, error }: Props) {
  const bookable = categories.filter((c) => !c.system)
  const groups = [
    ...(Object.keys(GROUP_LABELS) as CategoryGroup[]).map((group) => ({
      label: GROUP_LABELS[group],
      items: bookable.filter((c) => c.group === group),
    })),
    { label: 'Ohne Gruppe', items: bookable.filter((c) => c.group === null) },
  ].filter(({ items }) => items.length > 0)

  return (
    <label>
      Kategorie
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-invalid={error ? true : undefined}>
        <option value="">Bitte wählen</option>
        {groups.map(({ label, items }) => (
          <optgroup key={label} label={label}>
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
