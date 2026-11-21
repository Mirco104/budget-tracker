import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { CategoryTotal, HistoryData } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { ProgressBar } from '../components/ui/ProgressBar'
import { formatAmount, formatEuro, formatMonth, formatMonthShort, formatWholeNumber } from '../format'

type Side = 'expenses' | 'income'

// Colors from the design variables (index.css), so the chart follows light and dark mode
const COLORS: Record<Side, string> = { expenses: 'var(--chart-expense)', income: 'var(--chart-income)' }

interface MonthAmount {
  month: string
  amount: number
}

/**
 * Auswertung › Kategorien (R34, US39, decision 77): sum and average per category over the period of the look back,
 * and the months of one chosen category. All numbers come from the backend; this page only picks and formats them.
 */
export function CategoryAnalysis({ data }: { data: HistoryData }) {
  const [side, setSide] = useState<Side>('expenses')
  const [chosen, setChosen] = useState<number | null>(null)
  const lines = side === 'expenses' ? data.expensesByCategory : data.incomeByCategory
  const selected = lines.find((line) => line.id === chosen) ?? lines[0]
  // Bar length relative to the largest category, display only (decision 77)
  const largest = lines[0]?.total ?? 0

  if (data.months === 0) {
    return null
  }

  return (
    <div className={`category-analysis ${side}`}>
      <div className="chips" role="group" aria-label="Art">
        <button type="button" className="chip plain" aria-pressed={side === 'expenses'} onClick={() => setSide('expenses')}>
          Ausgaben
        </button>
        <button type="button" className="chip plain" aria-pressed={side === 'income'} onClick={() => setSide('income')}>
          Einnahmen
        </button>
      </div>

      {lines.length === 0 ? (
        <p className="hint">
          In diesem Zeitraum gibt es keine {side === 'expenses' ? 'Ausgaben' : 'Einnahmen'} mit Kategorie.
        </p>
      ) : (
        <>
          <table className="history category-totals" aria-label="Kategorien">
            <thead>
              <tr>
                <th>Kategorie</th>
                <th className="number">Summe (€)</th>
                <th className="number">Ø pro Monat (€)</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line) => (
                <CategoryRow
                  key={line.id}
                  line={line}
                  months={data.months}
                  largest={largest}
                  selected={line.id === selected?.id}
                  onSelect={() => setChosen(line.id)}
                />
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Gesamt</th>
                <td className="number">{formatAmount(side === 'expenses' ? data.total?.expenses ?? null : data.total?.income ?? null)}</td>
                <td className="number">{formatAmount(side === 'expenses' ? data.averageExpenses : data.averageIncome)}</td>
              </tr>
            </tfoot>
          </table>
          {selected && <CategoryChart data={data} side={side} line={selected} />}
        </>
      )}
    </div>
  )
}

function monthsText(count: number, months: number): string {
  return `in ${count} von ${months} ${months === 1 ? 'Monat' : 'Monaten'}`
}

// A click anywhere in the row chooses the category for the chart; the button keeps it reachable by keyboard
function CategoryRow({ line, months, largest, selected, onSelect }: {
  line: CategoryTotal
  months: number
  largest: number
  selected: boolean
  onSelect: () => void
}) {
  return (
    <tr className={`category-total expandable${selected ? ' highlighted' : ''}`} onClick={onSelect}>
      <th scope="row">
        <button type="button" aria-pressed={selected} onClick={(e) => { e.stopPropagation(); onSelect() }}>
          <span className="history-category-name">
            <CategoryIcon icon={line.icon} size={14} />
            {line.name}
          </span>
        </button>
        <span className="category-total-months">{monthsText(line.monthsWithAmount, months)}</span>
        <ProgressBar value={largest > 0 ? line.total / largest : 0} />
      </th>
      <td className="number">{formatAmount(line.total)}</td>
      <td className="number">{formatAmount(line.average)}</td>
    </tr>
  )
}

function AmountTooltip({ active, payload }: { active?: boolean; payload?: { payload: MonthAmount }[] }) {
  if (!active || !payload?.length) {
    return null
  }
  const entry = payload[0].payload
  return (
    <div className="chart-tooltip">
      <strong>{formatMonth(entry.month)}</strong>
      <span>{formatEuro(entry.amount)}</span>
    </div>
  )
}

/** US39: the amount of the chosen category in each counted month, with a line for its average. */
function CategoryChart({ data, side, line }: { data: HistoryData; side: Side; line: CategoryTotal }) {
  // A month without the category shows 0 (R34)
  const months: MonthAmount[] = data.history.map((month) => {
    const amounts = side === 'expenses' ? month.expenseCategories : month.incomeCategories
    return { month: month.month, amount: amounts.find((entry) => entry.id === line.id)?.amount ?? 0 }
  })
  return (
    <section className="category-chart">
      <h3>
        <span className="history-category-name">
          <CategoryIcon icon={line.icon} size={16} />
          Verlauf {line.name}
        </span>
      </h3>
      <div className="history-chart" role="img" aria-label={`Diagramm ${line.name} je Monat`}>
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={months} margin={{ top: 8, right: 24, bottom: 0, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="month" tickFormatter={formatMonthShort} fontSize={12} />
            <YAxis tickFormatter={formatWholeNumber} fontSize={12} width={56} />
            <Tooltip content={<AmountTooltip />} cursor={{ fill: 'var(--surface-3)', opacity: 0.6 }} />
            <Bar dataKey="amount" name={line.name} fill={COLORS[side]} />
            <ReferenceLine
              y={line.average}
              stroke="var(--muted)"
              strokeDasharray="4 4"
              label={{ value: 'Ø', position: 'right', fill: 'var(--muted)', fontSize: 12 }}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <p className="hint">Ø {formatEuro(line.average)} pro Monat</p>
    </section>
  )
}
