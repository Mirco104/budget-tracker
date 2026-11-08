import type { Category } from '../api/types'
import type { Filters } from '../filters'
import { Icon } from './ui/Icon'

export interface FilterOption {
  value: string
  label: string
}

/**
 * The same filters for Transaktionen (US26) and the import (US33, ticket #84): description, category, account (only
 * with more than one) and kind. The page does the filtering; extraCategoryOptions come before the categories.
 */
export function TransactionFilters({
  filters,
  onChange,
  categories,
  accounts,
  extraCategoryOptions = [],
}: {
  filters: Filters
  onChange: (filters: Filters) => void
  categories: Category[]
  accounts: FilterOption[]
  extraCategoryOptions?: FilterOption[]
}) {
  const set = (key: keyof Filters, value: string) => onChange({ ...filters, [key]: value })
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
        {extraCategoryOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
        {categories.map((category) => (
          <option key={category.id} value={category.id}>
            {category.archived ? `${category.name} (archiviert)` : category.name}
          </option>
        ))}
      </select>
      {accounts.length > 1 && (
        <select aria-label="Konto filtern" value={filters.account} onChange={(e) => set('account', e.target.value)}>
          <option value="">Alle Konten</option>
          {accounts.map((account) => (
            <option key={account.value} value={account.value}>
              {account.label}
            </option>
          ))}
        </select>
      )}
      <select aria-label="Art filtern" value={filters.kind} onChange={(e) => set('kind', e.target.value)}>
        <option value="">Alle Arten</option>
        <option value="RECURRING">Wiederkehrend</option>
        <option value="SINGLE">Einmalig</option>
        <option value="TRANSFER">Umbuchung</option>
      </select>
    </div>
  )
}
