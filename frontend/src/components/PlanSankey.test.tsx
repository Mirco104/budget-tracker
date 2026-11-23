import { render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Plan } from '../api/types'
import { PlanSankey } from './PlanSankey'

// Fictional plan, shaped like the backend response.
const plan = {
  month: '2026-11',
  income: 2500,
  fixed: 900,
  budgets: 400,
  saving: 300,
  unplanned: 900,
  categories: [
    { group: 'FIXED', categoryId: 1, categoryName: 'Wohnen', total: 900 },
    { group: 'BUDGET', categoryId: 3, categoryName: 'Lebensmittel', total: 400 },
  ],
  items: [{ id: 4, name: 'Notgroschen', group: 'SAVING', monthlyShare: 300 }],
} as unknown as Plan

afterEach(() => {
  vi.unstubAllGlobals()
})

// Field test after 0.3.0: on a phone held upright the flows show as a list
describe('PlanSankey', () => {
  it('shows the flows as a list on a narrow screen', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === '(max-width: 600px)',
      addEventListener: () => {},
      removeEventListener: () => {},
    }))
    render(<PlanSankey plan={plan} />)

    const list = screen.getByRole('list', { name: 'Wohin dein Einkommen im Plan fließt' })
    expect(list).toHaveTextContent('Einkommen2.500,00 €')
    const groups = within(list).getAllByRole('listitem').filter((item) => item.classList.contains('plan-flow-group'))
    const heads = ['Fixkosten900,00 €', 'Variable Kosten400,00 €', 'Sparen300,00 €', 'Nicht verplant900,00 €']
    expect(groups).toHaveLength(heads.length)
    heads.forEach((head, index) => expect(groups[index].querySelector('.plan-flow-head')).toHaveTextContent(head))
    expect(groups[2]).toHaveTextContent('Notgroschen300,00 €')
  })
})
