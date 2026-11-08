import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { TransactionFilters, type Filters } from './TransactionFilters'

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
