import { useState } from 'react'
import { useDashboard } from '../api/dashboard'
import type { CategoryLine, GroupLine } from '../api/types'
import { formatAmount, formatEuro, formatPercent, formatShare } from '../format'
import { GROUP_LABELS, UNGROUPED_LABEL } from '../labels'

type Unit = 'euro' | 'percent'

type Line = Pick<CategoryLine, 'target' | 'actual' | 'difference' | 'targetShare' | 'actualShare' | 'differenceShare'>

function signClass(value: number | null): string | undefined {
  return value !== null && value < 0 ? 'negative' : undefined
}

function groupKey(line: GroupLine): string {
  return line.group ?? 'UNGROUPED'
}

/** Soll, Ist and Abweichung cells; the Soll column is hidden on narrow screens. */
function AmountCells({ line, unit }: { line: Line; unit: Unit }) {
  const [target, actual, difference] =
    unit === 'euro'
      ? [line.target, line.actual, line.difference]
      : [line.targetShare, line.actualShare, line.differenceShare]
  const format = unit === 'euro' ? formatAmount : formatShare
  return (
    <>
      <td className={`number col-target ${signClass(target) ?? ''}`}>{format(target)}</td>
      <td className="number">{format(actual)}</td>
      <td className={`number ${signClass(difference) ?? ''}`}>{format(difference)}</td>
    </>
  )
}

export function DashboardPage({ month }: { month: string }) {
  const { data, isPending, error } = useDashboard(month)
  const [unit, setUnit] = useState<Unit>('euro')
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  if (isPending) {
    return <p>Lade Dashboard …</p>
  }
  if (error) {
    return <p className="error">Dashboard konnte nicht geladen werden: {error.message}</p>
  }

  const tile = (amount: number, share: number | null) =>
    unit === 'euro' ? formatEuro(amount) : formatPercent(share)
  const unitLabel = unit === 'euro' ? '€' : '%'

  function toggleGroup(key: string) {
    setCollapsed((current) => {
      const next = new Set(current)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  return (
    <section>
      <div className="dashboard-header">
        <h2>Dashboard</h2>
        <div className="unit-toggle" role="group" aria-label="Anzeige">
          <button type="button" aria-pressed={unit === 'euro'} onClick={() => setUnit('euro')}>
            €
          </button>
          <button type="button" aria-pressed={unit === 'percent'} onClick={() => setUnit('percent')}>
            %
          </button>
        </div>
      </div>

      <dl className="summary">
        <div>
          <dt>Einnahmen</dt>
          <dd>{tile(data.incomeTotal, data.incomeTotalShare)}</dd>
        </div>
        <div>
          <dt>Ausgaben</dt>
          <dd>{tile(data.expenses.actual, data.expenses.actualShare)}</dd>
        </div>
        <div>
          <dt>{data.surplusLabel}</dt>
          <dd className={signClass(data.surplus)}>{tile(data.surplus, data.surplusShare)}</dd>
        </div>
      </dl>

      <h3>Budget</h3>
      {data.categories.length === 0 ? (
        <p className="hint">In diesem Monat gibt es weder Soll-Werte noch Ausgaben.</p>
      ) : (
        <table className="budget">
          <thead>
            <tr>
              <th>Kategorie</th>
              <th className="number col-target">Soll ({unitLabel})</th>
              <th className="number">Ist ({unitLabel})</th>
              <th className="number">Abweichung ({unitLabel})</th>
            </tr>
          </thead>
          {data.groups.map((group) => {
            const key = groupKey(group)
            const label = group.group ? GROUP_LABELS[group.group] : UNGROUPED_LABEL
            const open = !collapsed.has(key)
            return (
              <tbody key={key}>
                <tr className="group-row">
                  <th scope="rowgroup">
                    <button type="button" aria-expanded={open} onClick={() => toggleGroup(key)}>
                      <span aria-hidden="true">{open ? '▾' : '▸'}</span> {label}
                    </button>
                  </th>
                  <AmountCells line={group} unit={unit} />
                </tr>
                {open &&
                  data.categories
                    .filter((line) => line.group === group.group)
                    .map((line) => (
                      <tr key={line.id} className="category-row">
                        <td>{line.name}</td>
                        <AmountCells line={line} unit={unit} />
                      </tr>
                    ))}
              </tbody>
            )
          })}
          <tfoot>
            <tr>
              <th scope="row">Gesamt</th>
              <AmountCells line={data.expenses} unit={unit} />
            </tr>
          </tfoot>
        </table>
      )}

      <h3>Einnahmen</h3>
      {data.income.length === 0 ? (
        <p className="hint">In diesem Monat gibt es keine Einnahmen.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Kategorie</th>
              <th className="number">Ist ({unitLabel})</th>
            </tr>
          </thead>
          <tbody>
            {data.income.map((line) => (
              <tr key={line.id}>
                <td>{line.name}</td>
                <td className="number">{unit === 'euro' ? formatAmount(line.actual) : formatShare(line.share)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
