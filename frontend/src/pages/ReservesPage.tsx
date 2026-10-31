import { useState, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useReserves } from '../api/reserves'
import type { ForecastMonth, HistoryMonth, ReserveData } from '../api/types'
import {
  formatAmount,
  formatDate,
  formatEuro,
  formatMonth,
  formatMonthShort,
  formatShare,
  formatWholeNumber,
} from '../format'
import { INTERVAL_LABELS } from '../labels'

type Unit = 'euro' | 'percent'

// The API takes any period from 1 to 120 months (R14); the page offers this selection
const PERIODS = [3, 6, 12, 24]
const DEFAULT_PERIOD = 12

// Storage can be missing or blocked (private window); the sections are then simply open
function readOpen(key: string): boolean {
  try {
    return localStorage.getItem(key) !== 'false'
  } catch {
    return true
  }
}

function writeOpen(key: string, open: boolean) {
  try {
    localStorage.setItem(key, String(open))
  } catch {
    // Not remembered, but the section still opens and closes
  }
}

/** A section with a heading that opens and closes it; the browser remembers the state. */
function Collapsible({ id, title, actions, children }: { id: string; title: string; actions?: ReactNode; children: ReactNode }) {
  const key = `budget.reserves.${id}.open`
  const [open, setOpen] = useState(() => readOpen(key))
  function toggle() {
    writeOpen(key, !open)
    setOpen(!open)
  }
  return (
    <>
      <div className="section-header">
        <h3>
          <button type="button" className="collapse-toggle" aria-expanded={open} onClick={toggle}>
            <span aria-hidden="true">{open ? '▾' : '▸'}</span> {title}
          </button>
        </h3>
        {open && actions}
      </div>
      {open && children}
    </>
  )
}

function averagePeriod(months: number): string {
  return months === 1 ? 'letzter Monat' : `letzte ${months} Monate`
}

function PeriodSelect({ period, onChange }: { period: number; onChange: (period: number) => void }) {
  return (
    <label className="period-select">
      Zeitraum
      <select value={period} onChange={(e) => onChange(Number(e.target.value))}>
        {PERIODS.map((months) => (
          <option key={months} value={months}>
            {months} Monate
          </option>
        ))}
      </select>
    </label>
  )
}

function AverageExpenses({ data, period }: { data: ReserveData; period: number }) {
  return (
    <>
      <dl className="summary">
        <div>
          <dt>
            Ø Ausgaben pro Monat
            {data.averageMonths > 0 && <span className="tile-note"> ({averagePeriod(data.averageMonths)})</span>}
          </dt>
          <dd>{formatEuro(data.averageExpenses)}</dd>
        </div>
      </dl>
      {data.averageMonths === 0 && (
        <p className="hint">
          Noch kein abgeschlossener Monat mit Einträgen. Der Durchschnitt erscheint ab dem Monat nach dem ersten
          Eintrag.
        </p>
      )}
      {data.averageMonths > 0 && data.averageMonths < period && (
        <p className="hint">
          Es gibt erst seit {data.averageMonths === 1 ? 'einem Monat' : `${data.averageMonths} Monaten`} Einträge,
          daher zählen nur diese.
        </p>
      )}
    </>
  )
}

function signClass(value: number | null): string | undefined {
  if (value === null || value === 0) {
    return undefined
  }
  return value < 0 ? 'negative' : 'positive'
}

// The short form replaces the long one on narrow screens (CSS), so four columns fit into 360 px
function MonthLabel({ month }: { month: string }) {
  return (
    <>
      <span className="month-long">{formatMonth(month)}</span>
      <span className="month-short">{formatMonthShort(month)}</span>
    </>
  )
}

// Expanded: one row per category, its amount in the income or expense column
function HistoryRows({ month, unit }: { month: HistoryMonth; unit: Unit }) {
  const [open, setOpen] = useState(false)
  const hasCategories = month.incomeCategories.length > 0 || month.expenseCategories.length > 0
  const value = (amount: number, share: number | null) => (unit === 'euro' ? formatAmount(amount) : formatShare(share))
  return (
    <>
      <tr className="history-month">
        <th scope="row">
          {hasCategories ? (
            <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
              <span aria-hidden="true">{open ? '▾' : '▸'}</span> <MonthLabel month={month.month} />
            </button>
          ) : (
            <MonthLabel month={month.month} />
          )}
        </th>
        <td className="number">{value(month.income, month.incomeShare)}</td>
        <td className="number">{value(month.expenses, month.expensesShare)}</td>
        <td className={`number ${signClass(unit === 'euro' ? month.surplus : month.surplusShare) ?? ''}`}>
          {value(month.surplus, month.surplusShare)}
        </td>
      </tr>
      {open &&
        month.incomeCategories.map((line) => (
          <tr key={`income-${line.name}`} className="history-category">
            <td>{line.name}</td>
            <td className="number">{value(line.amount, line.share)}</td>
            <td />
            <td />
          </tr>
        ))}
      {open &&
        month.expenseCategories.map((line) => (
          <tr key={`expense-${line.name}`} className="history-category">
            <td>{line.name}</td>
            <td />
            <td className="number">{value(line.amount, line.share)}</td>
            <td />
          </tr>
        ))}
    </>
  )
}

// Average and total have the same shares, as the backend delivers them once for both rows
function FooterRow({ label, data, unit, income, expenses, surplus }: {
  label: string
  data: ReserveData
  unit: Unit
  income: number | null
  expenses: number | null
  surplus: number | null
}) {
  const percent = unit === 'percent'
  return (
    <tr>
      <th scope="row">{label}</th>
      <td className="number">{percent ? formatShare(data.incomeShare) : formatAmount(income)}</td>
      <td className="number">{percent ? formatShare(data.expensesShare) : formatAmount(expenses)}</td>
      <td className={`number ${signClass(percent ? data.surplusShare : surplus) ?? ''}`}>
        {percent ? formatShare(data.surplusShare) : formatAmount(surplus)}
      </td>
    </tr>
  )
}

const INCOME_COLOR = '#1a7f37'
const EXPENSE_COLOR = '#cf222e'

/** Income and expenses per month of the look back, always in euros. */
function HistoryChart({ history }: { history: HistoryMonth[] }) {
  return (
    <div className="history-chart" role="img" aria-label="Diagramm Einnahmen und Ausgaben je Monat">
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={history} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" tickFormatter={formatMonthShort} fontSize={12} />
          <YAxis tickFormatter={formatWholeNumber} fontSize={12} width={56} />
          {/* Income before expenses, as in the table; Recharts would sort them by name */}
          <Tooltip
            labelFormatter={(month) => formatMonth(String(month))}
            formatter={(value) => formatEuro(Number(value))}
            itemSorter={(item) => (item.dataKey === 'income' ? 0 : 1)}
          />
          <Legend itemSorter={null} />
          <Bar dataKey="income" name="Einnahmen" fill={INCOME_COLOR} />
          <Bar dataKey="expenses" name="Ausgaben" fill={EXPENSE_COLOR} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

/** The months of the average with income, expenses and surplus, their averages and totals in the footer. */
function LookBack({ data }: { data: ReserveData }) {
  const [unit, setUnit] = useState<Unit>('euro')
  if (data.history.length === 0) {
    return null
  }
  const suffix = unit === 'euro' ? '(€)' : '(%)'
  const toggle = (
    <div className="unit-toggle" role="group" aria-label="Anzeige">
      <button type="button" aria-pressed={unit === 'euro'} onClick={() => setUnit('euro')}>
        €
      </button>
      <button type="button" aria-pressed={unit === 'percent'} onClick={() => setUnit('percent')}>
        %
      </button>
    </div>
  )
  return (
    <Collapsible id="lookBack" title="Rückblick" actions={toggle}>
      <HistoryChart history={data.history} />
      <table className="history" aria-label="Rückblick">
        <thead>
          <tr>
            <th>Monat</th>
            <th className="number">Einnahmen {suffix}</th>
            <th className="number">Ausgaben {suffix}</th>
            <th className="number">Überschuss {suffix}</th>
          </tr>
        </thead>
        <tbody>
          {data.history.map((m) => (
            <HistoryRows key={m.month} month={m} unit={unit} />
          ))}
        </tbody>
        <tfoot>
          <FooterRow label="Ø" data={data} unit={unit} income={data.averageIncome}
            expenses={data.averageExpenses} surplus={data.averageSurplus} />
          <FooterRow label="Gesamt" data={data} unit={unit} income={data.totalIncome}
            expenses={data.totalExpenses} surplus={data.totalSurplus} />
        </tfoot>
      </table>
    </Collapsible>
  )
}

function ReservePot({ data }: { data: ReserveData }) {
  if (data.reserves.length === 0) {
    return (
      <p className="hint">
        Keine seltenen Ausgaben in den nächsten 12 Monaten. Vierteljährliche, halbjährliche und jährliche
        wiederkehrende Ausgaben erscheinen hier.
      </p>
    )
  }
  return (
    <>
      <dl className="summary">
        <div>
          <dt>Monatlich zurücklegen</dt>
          <dd>{formatEuro(data.monthlyReserve)}</dd>
        </div>
        <div>
          <dt>Sollte jetzt angespart sein</dt>
          <dd>{formatEuro(data.reserveBalance)}</dd>
        </div>
      </dl>
      <p className="hint">
        Lege jeden Monat {formatEuro(data.monthlyReserve)} zur Seite, dann ist jede seltene Abbuchung gedeckt, wenn
        sie fällig wird.
      </p>
      <table>
        <thead>
          <tr>
            <th>Ausgabe</th>
            <th className="number col-optional">Betrag (€)</th>
            <th>Fällig am</th>
            <th className="number">Pro Monat (€)</th>
            <th className="number">Angespart (€)</th>
          </tr>
        </thead>
        <tbody>
          {data.reserves.map((line) => (
            <tr key={line.recurringId}>
              <td>
                {line.description}
                <span className="tile-note"> {INTERVAL_LABELS[line.interval]}</span>
              </td>
              <td className="number col-optional">{formatAmount(line.amount)}</td>
              <td>{formatDate(line.nextDate)}</td>
              <td className="number">{formatAmount(line.monthly)}</td>
              <td className="number">{formatAmount(line.saved)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

function ForecastRow({ month, largest }: { month: ForecastMonth; largest: number }) {
  // Bar length relative to the most expensive month, for display only
  const width = largest > 0 ? (month.total / largest) * 100 : 0
  return (
    <tr>
      <td>
        {month.entries.length > 0 ? (
          <details>
            <summary>{formatMonth(month.month)}</summary>
            <ul className="forecast-entries">
              {month.entries.map((entry) => (
                <li key={entry.kind === 'SINGLE' ? `single-${entry.id}` : `recurring-${entry.recurringId}-${entry.date}`}>
                  {formatDate(entry.date)} {entry.description}: {formatEuro(entry.amount)}
                  {entry.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
                </li>
              ))}
            </ul>
          </details>
        ) : (
          formatMonth(month.month)
        )}
      </td>
      <td className="number">{formatAmount(month.total)}</td>
      <td className="forecast-bar-cell" aria-hidden="true">
        <span className="forecast-bar" style={{ width: `${width}%` }} />
      </td>
    </tr>
  )
}

/**
 * Analysis (R14, US13): average expenses and look back over a chosen period, reserve pot for rare expenses and a
 * 12-month forecast.
 */
export function ReservesPage({ month }: { month: string }) {
  const [period, setPeriod] = useState(DEFAULT_PERIOD)
  const { data, isPending, error } = useReserves(month, period)

  if (isPending) {
    return <p>Lade Auswertung …</p>
  }
  if (error) {
    return <p className="error">Auswertung konnte nicht geladen werden: {error.message}</p>
  }

  const largest = Math.max(0, ...data.forecast.map((m) => m.total))

  return (
    <section>
      <h2>Auswertung</h2>
      <PeriodSelect period={period} onChange={setPeriod} />
      <AverageExpenses data={data} period={period} />
      <LookBack data={data} />

      <h3>Rücklagen</h3>
      <ReservePot data={data} />

      <Collapsible id="forecast" title="Vorschau 12 Monate">
        <p className="hint">
          Alle gebuchten Ausgaben je Monat, wiederkehrende und einzelne; teure Monate haben einen längeren Balken.
        </p>
        <table className="forecast" aria-label="Vorschau 12 Monate">
          <thead>
            <tr>
              <th>Monat</th>
              <th className="number">Ausgaben (€)</th>
              <th className="forecast-bar-cell" aria-hidden="true" />
            </tr>
          </thead>
          <tbody>
            {data.forecast.map((m) => (
              <ForecastRow key={m.month} month={m} largest={largest} />
            ))}
          </tbody>
        </table>
      </Collapsible>
    </section>
  )
}
