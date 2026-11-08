import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import type { Filters } from '../filters'
import { TransactionFilters } from './TransactionFilters'

// Fictional values
const categories: Category[] = [
  { id: 9, name: 'Alt', icon: 'tag', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: true, incomeOnly: false },
  { id: 1, name: 'Wohnen', icon: 'home', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
]
const empty: Filters = { query: '', category: '', account: '', kind: '' }

describe('TransactionFilters', () => {
  it('shows search, category and kind, and the account only with more than one option', () => {
    const { rerender } = render(
      <TransactionFilters filters={empty} onChange={() => {}} categories={categories} accounts={[{ value: '1', label: 'Girokonto' }]} />,
    )
    expect(screen.getByLabelText('Beschreibung suchen')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Alt (archiviert)' })).toBeInTheDocument()
    expect(screen.getByLabelText('Art filtern')).toBeInTheDocument()
    expect(screen.queryByLabelText('Konto filtern')).not.toBeInTheDocument()

    rerender(
      <TransactionFilters
        filters={empty}
        onChange={() => {}}
        categories={categories}
        accounts={[
          { value: '1', label: 'Girokonto' },
          { value: '2', label: 'Depot' },
        ]}
      />,
    )
    expect(screen.getByLabelText('Konto filtern')).toBeInTheDocument()
  })

  it('offers extra category options before the categories', () => {
    render(
      <TransactionFilters
        filters={empty}
        onChange={() => {}}
        categories={categories}
        accounts={[]}
        extraCategoryOptions={[{ value: 'open', label: 'Ohne Kategorie (3)' }]}
      />,
    )
    const options = screen.getAllByRole('option').map((option) => option.textContent)
    expect(options.slice(0, 3)).toEqual(['Alle Kategorien', 'Ohne Kategorie (3)', 'Alt (archiviert)'])
  })

  it('shows the count of each option and hides the options without a hit, but keeps the chosen one', () => {
    const counts = {
      category: new Map([
        ['1', 3],
        ['open', 2],
      ]),
      account: new Map([['2', 4]]),
      kind: new Map([['SINGLE', 4]]),
    }
    render(
      <TransactionFilters
        filters={{ ...empty, kind: 'RECURRING' }}
        onChange={() => {}}
        categories={categories}
        accounts={[
          { value: '1', label: 'Girokonto' },
          { value: '2', label: 'Depot' },
        ]}
        extraCategoryOptions={[
          { value: 'open', label: 'Ohne Kategorie' },
          { value: 'transfer', label: 'Umbuchung' },
        ]}
        counts={counts}
      />,
    )

    const names = (label: string) =>
      Array.from((screen.getByLabelText(label) as HTMLSelectElement).options).map((option) => option.textContent)
    expect(names('Kategorie filtern')).toEqual(['Alle Kategorien', 'Ohne Kategorie (2)', 'Wohnen (3)'])
    expect(names('Konto filtern')).toEqual(['Alle Konten', 'Depot (4)'])
    // Wiederkehrend is chosen, so it stays with (0); Umbuchung is no kind but a category option
    expect(names('Art filtern')).toEqual(['Alle Arten', 'Wiederkehrend (0)', 'Einmalig (4)'])
  })

  it('reports every change with the other filters kept', () => {
    const onChange = vi.fn()
    render(
      <TransactionFilters filters={{ ...empty, kind: 'SINGLE' }} onChange={onChange} categories={categories} accounts={[]} />,
    )

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'Miete' } })
    expect(onChange).toHaveBeenLastCalledWith({ ...empty, kind: 'SINGLE', query: 'Miete' })
    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: '1' } })
    expect(onChange).toHaveBeenLastCalledWith({ ...empty, kind: 'SINGLE', category: '1' })
  })
})
