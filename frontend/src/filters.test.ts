import { describe, expect, it } from 'vitest'
import type { Category } from './api/types'
import { categoryFits, facetCounts, NO_FILTERS, type Filters } from './filters'

// Fictional entries
interface Item {
  text: string
  category: string
  account: string
  kind: string
}

const items: Item[] = [
  { text: 'Miete Wohnung', category: '1', account: 'giro', kind: 'RECURRING' },
  { text: 'Miete Garage', category: '1', account: 'giro', kind: 'SINGLE' },
  { text: 'Kino', category: '8', account: 'giro', kind: 'SINGLE' },
  { text: 'Kino', category: '8', account: 'pocket', kind: 'SINGLE' },
]

function matches(item: Item, filters: Filters): boolean {
  return (
    (filters.query === '' || item.text.toLowerCase().includes(filters.query.toLowerCase())) &&
    (filters.category === '' || item.category === filters.category) &&
    (filters.account === '' || item.account === filters.account) &&
    (filters.kind === '' || item.kind === filters.kind)
  )
}

const keys = {
  category: (item: Item) => item.category,
  account: (item: Item) => item.account,
  kind: (item: Item) => item.kind,
  direction: () => '',
}

describe('facetCounts', () => {
  it('counts every field with the other filters', () => {
    const counts = facetCounts(items, { ...NO_FILTERS, query: 'miete' }, matches, keys)

    expect(Object.fromEntries(counts.category)).toEqual({ '1': 2 })
    expect(Object.fromEntries(counts.account)).toEqual({ giro: 2 })
    expect(Object.fromEntries(counts.kind)).toEqual({ RECURRING: 1, SINGLE: 1 })
  })

  it('leaves out the filter of the counted field itself', () => {
    const counts = facetCounts(items, { ...NO_FILTERS, category: '8', kind: 'SINGLE' }, matches, keys)

    // The category counts with the kind only, so another category can still be chosen
    expect(Object.fromEntries(counts.category)).toEqual({ '1': 1, '8': 2 })
    expect(Object.fromEntries(counts.account)).toEqual({ giro: 1, pocket: 1 })
    expect(Object.fromEntries(counts.kind)).toEqual({ SINGLE: 2 })
  })
})

// R32: a parent filter also fits the entries of its subcategories
describe('categoryFits', () => {
  const categories: Category[] = [
    { id: 2, name: 'Freizeit', icon: 'music', archived: false, incomeOnly: false, parentId: null, path: 'Freizeit' },
    { id: 6, name: 'Kino', icon: 'music', archived: false, incomeOnly: false, parentId: 2, path: 'Freizeit › Kino' },
  ]

  it('fits the category itself, its subcategories and no filter', () => {
    expect(categoryFits('2', '2', categories)).toBe(true)
    expect(categoryFits('6', '2', categories)).toBe(true)
    expect(categoryFits('6', '', categories)).toBe(true)
  })

  it('does not fit the parent for a subcategory filter or another category', () => {
    expect(categoryFits('2', '6', categories)).toBe(false)
    expect(categoryFits('transfer', '2', categories)).toBe(false)
    expect(categoryFits('9', '2', categories)).toBe(false)
  })
})
