import { useState } from 'react'
import { useDashboard } from '../api/dashboard'
import type { CategoryLine } from '../api/types'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { formatAmount, formatEuro, formatPercent, formatShare } from '../format'

type Unit = 'euro' | 'percent'

type Line = Pick<CategoryLine, 'target' | 'actual' | 'difference' | 'targetShare' | 'actualShare' | 'differenceShare'>

function signClass(value: number | null): string | undefined {
  return value !== null && value < 0 ? 'negative' : undefined
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

interface Props {
  month: string
  /** Shows the month navigation in the page header (US8). */
  onMonthChange?: (month: string) => void
}

export function DashboardPage({ month, onMonthChange }: Props) {
  const [unit, setUnit] = useState<Unit>('euro')

  return (
    <section>
      <PageHeader title="Übersicht">
        {onMonthChange && <MonthNav month={month} onChange={onMonthChange} />}
        <div className="unit-toggle" role="group" aria-label="Anzeige">
          <button type="button" aria-pressed={unit === 'euro'} onClick={() => setUnit('euro')}>
            €
          </button>
          <button type="button" aria-pressed={unit === 'percent'} onClick={() => setUnit('percent')}>
            %
          </button>
        </div>
      </PageHeader>
      <DashboardContent month={month} unit={unit} />
    </section>
  )
}

function DashboardContent({ month, unit }: { month: string; unit: Unit }) {
  const { data, isPending, error } = useDashboard(month)

  if (isPending) {
    return <p>Lade Dashboard …</p>
  }
  if (error) {
    return <p className="error">Dashboard konnte nicht geladen werden: {error.message}</p>
  }

  const tile = (amount: number, share: number | null) =>
    unit === 'euro' ? formatEuro(amount) : formatPercent(share)
  const unitLabel = unit === 'euro' ? '€' : '%'

  return (
    <>
      <dl className="summary">
        <div>
          <dt>Einnahmen</dt>
          <dd>{tile(data.incomeTotal, data.incomeTotalShare)}</dd>
        </div>
        <div>
          <dt>Ausgaben</dt>
          <dd>{tile(data.expenses.actual, data.expenses.actualShare)}</dd>
          {/* R10: recurring transactions are the fixed costs, single transactions the rest */}
          <dd className="breakdown">
            davon wiederkehrend <span>{tile(data.expenses.recurring, data.expenses.recurringShare)}</span>
          </dd>
          <dd className="breakdown">
            davon einmalig <span>{tile(data.expenses.oneOff, data.expenses.oneOffShare)}</span>
          </dd>
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
          <tbody>
            {data.categories.map((line) => (
              <tr key={line.id}>
                <td>{line.name}</td>
                <AmountCells line={line} unit={unit} />
              </tr>
            ))}
          </tbody>
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
    </>
  )
}
