import type { ReactNode } from 'react'
import type { Category } from '../api/types'
import type { FacetCounts, FacetField, Filters } from '../filters'
import { Icon } from './ui/Icon'

export interface FilterOption {
  value: string
  label: string
}

// An Umbuchung is chosen like a category when booking, so it is a category option, not a kind (ticket #84)
// Transaktionen: fix and variable expenses (P5); the import keeps its own kinds
const KINDS: FilterOption[] = [
  { value: 'FIX', label: 'Fix' },
  { value: 'VARIABLE', label: 'Variabel' },
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
  directions,
  toggle,
  expanded = true,
}: {
  filters: Filters
  onChange: (filters: Filters) => void
  categories: Category[]
  accounts: FilterOption[]
  extraCategoryOptions?: FilterOption[]
  counts?: FacetCounts
  kinds?: FilterOption[]
  /** 27f4: Einnahmen / Ausgaben; without it the bar has no such filter (the import) */
  directions?: FilterOption[]
  /** 27f4: a button after the search that folds the other filters (Transaktionen) */
  toggle?: ReactNode
  /** 27f4: false shows only the search and the toggle */
  expanded?: boolean
}) {
  const set = (key: keyof Filters, value: string) => onChange({ ...filters, [key]: value })

  // R32: a parent counts the hits of its subcategories too, like its filter
  function hits(field: FacetField, value: string): number {
    if (!counts) {
      return 0
    }
    const own = counts[field].get(value) ?? 0
    if (field !== 'category') {
      return own
    }
    return categories
      .filter((c) => String(c.parentId) === value)
      .reduce((sum, child) => sum + (counts.category.get(String(child.id)) ?? 0), own)
  }

  function options(field: FacetField, all: FilterOption[]) {
    return all
      .filter((option) => !counts || hits(field, option.value) > 0 || option.value === filters[field])
      .map((option) => (
        <option key={option.value} value={option.value}>
          {counts ? `${option.label} (${hits(field, option.value)})` : option.label}
        </option>
      ))
  }

  const categoryOptions = [
    ...extraCategoryOptions,
    ...categories.map((category) => ({
      value: String(category.id),
      label: category.archived ? `${category.path} (archiviert)` : category.path,
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
      {toggle}
      {expanded && (
        <div className={toggle ? 'filter-more' : 'filter-contents'}>
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
          {directions && (
            <select
              aria-label="Einnahmen oder Ausgaben filtern"
              value={filters.direction}
              // Fix and variable are kinds of expenses; with Einnahmen they do not apply
              onChange={(e) =>
                onChange({ ...filters, direction: e.target.value, kind: e.target.value === 'INCOME' ? '' : filters.kind })
              }
            >
              <option value="">Einnahmen und Ausgaben</option>
              {options('direction', directions)}
            </select>
          )}
          {filters.direction !== 'INCOME' && (
            <select aria-label="Art filtern" value={filters.kind} onChange={(e) => set('kind', e.target.value)}>
              <option value="">{directions ? 'Fix und variabel' : 'Alle Arten'}</option>
              {options('kind', kinds)}
            </select>
          )}
        </div>
      )}
    </div>
  )
}
