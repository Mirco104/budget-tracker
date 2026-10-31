import type { Category } from '../api/types'

interface Props {
  categories: Category[]
  /** Selected category id as a string, '' for none. */
  value: string
  onChange: (value: string) => void
  error?: string
}

/**
 * Category dropdown, in the order of the categories (sortOrder). Archived categories are left out unless one is
 * already selected, so editing an old entry keeps its category (US10).
 */
export function CategorySelect({ categories, value, onChange, error }: Props) {
  return (
    <label>
      Kategorie
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-invalid={error ? true : undefined}>
        <option value="">Bitte wählen</option>
        {categories.filter((c) => !c.archived || String(c.id) === value).map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </select>
      {error && <span className="error">{error}</span>}
    </label>
  )
}
