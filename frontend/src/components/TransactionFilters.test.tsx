import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { DIRECTIONS, type Filters } from '../filters'
import { TransactionFilters } from './TransactionFilters'

// Fictional values
const categories: Category[] = [
  { id: 9, name: 'Alt', icon: 'tag', archived: true, incomeOnly: false },
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false },
]
const empty: Filters = { query: '', category: '', account: '', kind: '', direction: '' }

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
      kind: new Map([['VARIABLE', 4]]),
      direction: new Map(),
    }
    render(
      <TransactionFilters
        filters={{ ...empty, kind: 'FIX' }}
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
    // Fix is chosen, so it stays with (0); Umbuchung is no kind but a category option
    expect(names('Art filtern')).toEqual(['Alle Arten', 'Fix (0)', 'Variabel (4)'])
  })

  it('reports every change with the other filters kept', () => {
    const onChange = vi.fn()
    render(
      <TransactionFilters filters={empty} onChange={onChange} categories={categories} accounts={[]} />,
    )

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'Miete' } })
    expect(onChange).toHaveBeenLastCalledWith({ ...empty, query: 'Miete' })
    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: '1' } })
    expect(onChange).toHaveBeenLastCalledWith({ ...empty, category: '1' })
  })

  // 27f4 (decision 60): Einnahmen / Ausgaben and fix / variabel are two filters; with Einnahmen fix / variabel goes
  it('offers Einnahmen and Ausgaben apart from fix and variable', () => {
    const onChange = vi.fn()
    const { rerender } = render(
      <TransactionFilters
        filters={{ ...empty, kind: 'FIX' }}
        onChange={onChange}
        categories={categories}
        accounts={[]}
        directions={DIRECTIONS}
      />,
    )
    const kinds = Array.from((screen.getByLabelText('Art filtern') as HTMLSelectElement).options).map((o) => o.textContent)
    expect(kinds).toEqual(['Fix und variabel', 'Fix', 'Variabel'])
    fireEvent.change(screen.getByLabelText('Einnahmen oder Ausgaben filtern'), { target: { value: 'INCOME' } })
    expect(onChange).toHaveBeenCalledWith({ ...empty, direction: 'INCOME', kind: '' })

    rerender(
      <TransactionFilters
        filters={{ ...empty, direction: 'INCOME' }}
        onChange={onChange}
        categories={categories}
        accounts={[]}
        directions={DIRECTIONS}
      />,
    )
    expect(screen.queryByLabelText('Art filtern')).not.toBeInTheDocument()
  })
})
