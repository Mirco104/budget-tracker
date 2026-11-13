/** The values of the shared filter bar of Transaktionen and the import (US26, US33); '' means no filter. */
export interface Filters {
  query: string
  category: string
  account: string
  kind: string
}

export const NO_FILTERS: Filters = { query: '', category: '', account: '', kind: '' }

/**
 * The start of the import filters (ticket #84, after the test): only one-off rows, the series rows and transfers are
 * handled by their cards and choices and shown with Wiederkehrend, Umbuchung or Alle Arten.
 */
export const IMPORT_FILTERS: Filters = { ...NO_FILTERS, kind: 'SINGLE' }

/** The fields of the filter bar whose options get a count. */
export type FacetField = 'category' | 'account' | 'kind'

/** The hits per option value of each field. */
export type FacetCounts = Record<FacetField, Map<string, number>>

/**
 * The hits of every option of category, account and kind (ticket #84): each field is counted with the other filters,
 * without its own, so another option of the same field can still be chosen. matches is the filter of the page and keys
 * give the option value of an item per field.
 */
export function facetCounts<T>(
  items: T[],
  filters: Filters,
  matches: (item: T, filters: Filters) => boolean,
  keys: Record<FacetField, (item: T) => string>,
): FacetCounts {
  const count = (field: FacetField) => {
    const result = new Map<string, number>()
    const others = { ...filters, [field]: '' }
    for (const item of items) {
      if (matches(item, others)) {
        const key = keys[field](item)
        result.set(key, (result.get(key) ?? 0) + 1)
      }
    }
    return result
  }
  return { category: count('category'), account: count('account'), kind: count('kind') }
}

/** The kinds of the import: a row that fulfils a planned term or belongs to a new item is "Wiederkehrend". */
export const IMPORT_KINDS: { value: string; label: string }[] = [
  { value: 'RECURRING', label: 'Wiederkehrend' },
  { value: 'SINGLE', label: 'Einmalig' },
]
