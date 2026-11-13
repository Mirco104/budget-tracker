import type { Category } from '../api/types'
import type { FacetCounts, FacetField, Filters } from '../filters'
import { Icon } from './ui/Icon'

export interface FilterOption {
  value: string
  label: string
}

// An Umbuchung is chosen like a category when booking, so it is a category option, not a kind (ticket #84)
// Transaktionen: fix and variable expenses (P5) and income; the import keeps its own kinds
const KINDS: FilterOption[] = [
  { value: 'FIX', label: 'Fix' },
  { value: 'VARIABLE', label: 'Variabel' },
  { value: 'INCOME', label: 'Einnahmen' },
]

/**
 * The same filters for Transaktionen (US26) and the import (US33, ticket #84): description, category, account (only
 * with more than one) and kind. The page does the filtering; extraCategoryOptions come before the categories. With
 * counts each option shows its hits and an option without one is hidden, unless it is chosen.
 */
export function TransactionFilters({
  filters,
  onChange,
  categories,
  accounts,
  extraCategoryOptions = [],
  counts,
  kinds = KINDS,
}: {
  filters: Filters
  onChange: (filters: Filters) => void
  categories: Category[]
  accounts: FilterOption[]
  extraCategoryOptions?: FilterOption[]
  counts?: FacetCounts
  kinds?: FilterOption[]
}) {
  const set = (key: keyof Filters, value: string) => onChange({ ...filters, [key]: value })

  function options(field: FacetField, all: FilterOption[]) {
    return all
      .filter((option) => !counts || (counts[field].get(option.value) ?? 0) > 0 || option.value === filters[field])
      .map((option) => (
        <option key={option.value} value={option.value}>
          {counts ? `${option.label} (${counts[field].get(option.value) ?? 0})` : option.label}
        </option>
      ))
  }

  const categoryOptions = [
    ...extraCategoryOptions,
    ...categories.map((category) => ({
      value: String(category.id),
      label: category.archived ? `${category.name} (archiviert)` : category.name,
    })),
  ]

  return (
    <div className="filter-row">
      <label className="search">
        <Icon name="search" size={18} />
        <input
          type="search"
          placeholder="Beschreibung suchen"
          aria-label="Beschreibung suchen"
          value={filters.query}
          onChange={(e) => set('query', e.target.value)}
        />
      </label>
      <select aria-label="Kategorie filtern" value={filters.category} onChange={(e) => set('category', e.target.value)}>
        <option value="">Alle Kategorien</option>
        {options('category', categoryOptions)}
      </select>
      {accounts.length > 1 && (
        <select aria-label="Konto filtern" value={filters.account} onChange={(e) => set('account', e.target.value)}>
          <option value="">Alle Konten</option>
          {options('account', accounts)}
        </select>
      )}
      <select aria-label="Art filtern" value={filters.kind} onChange={(e) => set('kind', e.target.value)}>
        <option value="">Alle Arten</option>
        {options('kind', kinds)}
      </select>
    </div>
  )
}
