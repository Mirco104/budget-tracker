import { describe, expect, it } from 'vitest'
import { facetCounts, NO_FILTERS, type Filters } from './filters'

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
