import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { errorText } from '../api/client'
import { useHistory, useReserves } from '../api/reserves'
import type { ForecastMonth, HistoryData, HistoryMonth, PotMonth, ReserveData } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { InfoTip } from '../components/ui/InfoTip'
import { PageHeader } from '../components/ui/PageHeader'
import {
  currentMonth,
  formatAmount,
  formatDate,
  formatEuro,
  formatMonth,
  formatMonthShort,
  formatWholeNumber,
  shiftMonth,
} from '../format'
import { INTERVAL_LABELS } from '../labels'
import { EVALUATION_ROUTES, useEvaluationPage } from '../router'

/** Analysis (R14, US13, decision 29): look back over a period and outlook from the current month on. */
export function EvaluationPage() {
  const page = useEvaluationPage()
  return (
    <section>
      <PageHeader title="Auswertung" />
      <nav className="subtabs" aria-label="Auswertung">
        <a href={EVALUATION_ROUTES.history} aria-current={page === 'history' ? 'page' : undefined}>
          Rückblick
        </a>
        <a href={EVALUATION_ROUTES.outlook} aria-current={page === 'outlook' ? 'page' : undefined}>
          Vorausschau
        </a>
      </nav>
      {page === 'history' ? <LookBack /> : <Outlook />}
    </section>
  )
}

// --- Rückblick ---

type Preset = 'last3' | 'last6' | 'last12' | 'lastYear' | 'thisYear' | 'custom'

interface MonthPeriod {
  from: string
  to: string
}

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/

/** The quick choices around the current month; the look back always ends before it (R14). */
function presets(current: string): { value: Preset; label: string; period: MonthPeriod | null }[] {
  const last = shiftMonth(current, -1)
  const year = Number(current.slice(0, 4))
  const list: { value: Preset; label: string; period: MonthPeriod | null }[] = [
    { value: 'last3', label: 'Letzte 3 Monate', period: { from: shiftMonth(current, -3), to: last } },
    { value: 'last6', label: 'Letzte 6 Monate', period: { from: shiftMonth(current, -6), to: last } },
    { value: 'last12', label: 'Letzte 12 Monate', period: { from: shiftMonth(current, -12), to: last } },
    { value: 'lastYear', label: `Jahr ${year - 1}`, period: { from: `${year - 1}-01`, to: `${year - 1}-12` } },
  ]
  // In January this year has no completed month yet
  if (current.slice(5) !== '01') {
    list.push({ value: 'thisYear', label: `${year} bisher`, period: { from: `${year}-01`, to: last } })
  }
  list.push({ value: 'custom', label: 'Zeitraum …', period: null })
  return list
}

/** Months from one month to another, both included (display only). */
function monthCount(period: MonthPeriod): number {
  const [fromYear, fromMonth] = period.from.split('-').map(Number)
  const [toYear, toMonth] = period.to.split('-').map(Number)
  return (toYear - fromYear) * 12 + (toMonth - fromMonth) + 1
}

function periodLabel(period: MonthPeriod): string {
  return period.from === period.to
    ? formatMonth(period.from)
    : `${formatMonth(period.from)} – ${formatMonth(period.to)}`
}

function signClass(value: number | null): string | undefined {
  if (value === null || value === 0) {
    return undefined
  }
  return value < 0 ? 'negative' : 'positive'
}

function LookBack() {
  const current = currentMonth()
  const choices = presets(current)
  const [preset, setPreset] = useState<Preset>('last12')
  const [custom, setCustom] = useState<MonthPeriod>(() => choices[2].period as MonthPeriod)
  const period = preset === 'custom' ? custom : (choices.find((c) => c.value === preset)?.period as MonthPeriod)
  const complete = MONTH_PATTERN.test(period.from) && MONTH_PATTERN.test(period.to)
  const { data, error, isPending } = useHistory(period.from, period.to, complete)
  const [active, setActive] = useState<string | null>(null)

  function choose(next: Preset) {
    if (next === 'custom' && preset !== 'custom') {
      // Starts with the period shown so far
      setCustom(period)
    }
    setPreset(next)
  }

  return (
    <>
      <div className="filters">
        <div className="chips" role="group" aria-label="Zeitraum">
          {choices.map((choice) => (
            <button
              key={choice.value}
              type="button"
              className="chip plain"
              aria-pressed={preset === choice.value}
              onClick={() => choose(choice.value)}
            >
              {choice.label}
            </button>
          ))}
        </div>
        {preset === 'custom' && (
          <div className="period-fields">
            <label className="field">
              Von
              <input
                type="month"
                value={custom.from}
                max={shiftMonth(current, -1)}
                onChange={(e) => setCustom({ ...custom, from: e.target.value })}
              />
            </label>
            <label className="field">
              Bis
              <input
                type="month"
                value={custom.to}
                max={shiftMonth(current, -1)}
                onChange={(e) => setCustom({ ...custom, to: e.target.value })}
              />
            </label>
          </div>
        )}
        {complete && <p className="hint period-summary">{periodLabel(period)}</p>}
      </div>

      {!complete && <p className="hint">Bitte „Von“ und „Bis“ vollständig angeben.</p>}
      {complete && isPending && <p>Lade Rückblick …</p>}
      {error && <p className="error">Rückblick konnte nicht geladen werden: {errorText(error, 'Unbekannter Fehler')}</p>}
      {data && !error && <LookBackContent data={data} requested={monthCount(period)} active={active} onActive={setActive} />}
    </>
  )
}

function LookBackContent({ data, requested, active, onActive }: {
  data: HistoryData
  requested: number
  active: string | null
  onActive: (month: string | null) => void
}) {
  return (
    <>
      <dl className="summary">
        <div>
          <dt>Ø Einnahmen</dt>
          <dd>{formatEuro(data.averageIncome)}</dd>
        </div>
        <div>
          <dt>Ø Ausgaben</dt>
          <dd>{formatEuro(data.averageExpenses)}</dd>
        </div>
        <div>
          <dt>Ø Überschuss</dt>
          <dd className={data.averageSurplus !== null && data.averageSurplus < 0 ? 'negative' : undefined}>
            {formatEuro(data.averageSurplus)}
          </dd>
        </div>
      </dl>
      {data.months === 0 && (
        <p className="hint">
          In diesem Zeitraum gibt es noch keinen Monat mit Einträgen. Gezählt wird ab dem Monat des ersten Eintrags.
        </p>
      )}
      {data.months > 0 && data.months < requested && (
        <p className="hint">
          Es zählen {data.months === 1 ? '1 Monat' : `${data.months} Monate`} von {requested}, weil es erst ab{' '}
          {formatMonth(data.history[0].month)} Einträge gibt.
        </p>
      )}
      {data.history.length > 0 && (
        <>
          <HistoryChart history={data.history} active={active} onActive={onActive} />
          <table className="history" aria-label="Rückblick">
            <thead>
              <tr>
                <th>Monat</th>
                <th className="number">Einnahmen (€)</th>
                <th className="number">Ausgaben (€)</th>
                <th className="number">Überschuss (€)</th>
              </tr>
            </thead>
            <tbody>
              {data.history.map((m) => (
                <HistoryRows key={m.month} month={m} active={active === m.month} onActive={onActive} />
              ))}
            </tbody>
            <tfoot>
              <FooterRow label="Ø" income={data.averageIncome} expenses={data.averageExpenses} surplus={data.averageSurplus} />
              <FooterRow
                label="Gesamt"
                income={data.total?.income ?? null}
                expenses={data.total?.expenses ?? null}
                surplus={data.total?.surplus ?? null}
              />
            </tfoot>
          </table>
        </>
      )}
    </>
  )
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

// Expanded: one row per category with its icon, the amount in the income or expense column. Hovering any row of
// the month marks it here and in the chart.
function HistoryRows({ month, active, onActive }: {
  month: HistoryMonth
  active: boolean
  onActive: (month: string | null) => void
}) {
  const [open, setOpen] = useState(false)
  const hasCategories = month.incomeCategories.length > 0 || month.expenseCategories.length > 0
  const hover = {
    onMouseEnter: () => onActive(month.month),
    onMouseLeave: () => onActive(null),
  }
  const highlight = active ? ' highlighted' : ''
  return (
    <>
      {/* A click anywhere in the row opens it; the button keeps it reachable by keyboard */}
      <tr
        className={`history-month${hasCategories ? ' expandable' : ''}${highlight}`}
        onClick={hasCategories ? () => setOpen(!open) : undefined}
        {...hover}
      >
        <th scope="row">
          {hasCategories ? (
            <button type="button" aria-expanded={open}>
              <span aria-hidden="true">{open ? '▾' : '▸'}</span> <MonthLabel month={month.month} />
            </button>
          ) : (
            <MonthLabel month={month.month} />
          )}
        </th>
        <td className="number">{formatAmount(month.income)}</td>
        <td className="number">{formatAmount(month.expenses)}</td>
        <td className={`number ${signClass(month.surplus) ?? ''}`}>{formatAmount(month.surplus)}</td>
      </tr>
      {open &&
        month.incomeCategories.map((line) => (
          <tr key={`income-${line.id}`} className={`history-category${highlight}`} {...hover}>
            <td>
              <span className="history-category-name">
                <CategoryIcon icon={line.icon} size={14} />
                {line.name}
              </span>
            </td>
            <td className="number">{formatAmount(line.amount)}</td>
            <td />
            <td />
          </tr>
        ))}
      {open &&
        month.expenseCategories.map((line) => (
          <tr key={`expense-${line.id}`} className={`history-category${highlight}`} {...hover}>
            <td>
              <span className="history-category-name">
                <CategoryIcon icon={line.icon} size={14} />
                {line.name}
              </span>
            </td>
            <td />
            <td className="number">{formatAmount(line.amount)}</td>
            <td />
          </tr>
        ))}
    </>
  )
}

function FooterRow({ label, income, expenses, surplus }: {
  label: string
  income: number | null
  expenses: number | null
  surplus: number | null
}) {
  return (
    <tr>
      <th scope="row">{label}</th>
      <td className="number">{formatAmount(income)}</td>
      <td className="number">{formatAmount(expenses)}</td>
      <td className={`number ${signClass(surplus) ?? ''}`}>{formatAmount(surplus)}</td>
    </tr>
  )
}

// Colors from the design variables (index.css), so the chart follows light and dark mode
const INCOME_COLOR = 'var(--chart-income)'
const EXPENSE_COLOR = 'var(--chart-expense)'

// Other months fade while one month is marked
const FADED_OPACITY = 0.3

/** US13: the tooltip names income, expenses and the surplus of the month. */
function MonthTooltip({ active, payload }: { active?: boolean; payload?: { payload: HistoryMonth }[] }) {
  if (!active || !payload?.length) {
    return null
  }
  const month = payload[0].payload
  return (
    <div className="chart-tooltip">
      <strong>{formatMonth(month.month)}</strong>
      <span>Einnahmen {formatEuro(month.income)}</span>
      <span>Ausgaben {formatEuro(month.expenses)}</span>
      <span className={signClass(month.surplus)}>Überschuss {formatEuro(month.surplus)}</span>
    </div>
  )
}

/** Income and expenses per month of the look back. The marked month is shared with the table. */
function HistoryChart({ history, active, onActive }: {
  history: HistoryMonth[]
  active: string | null
  onActive: (month: string | null) => void
}) {
  const opacity = (month: string) => (active === null || active === month ? 1 : FADED_OPACITY)
  return (
    <div className="history-chart" role="img" aria-label="Diagramm Einnahmen und Ausgaben je Monat">
      <ResponsiveContainer width="100%" height={220}>
        <BarChart
          data={history}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          onMouseMove={(state) => onActive(state.activeLabel === undefined ? null : String(state.activeLabel))}
          onMouseLeave={() => onActive(null)}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" tickFormatter={formatMonthShort} fontSize={12} />
          <YAxis tickFormatter={formatWholeNumber} fontSize={12} width={56} />
          <Tooltip content={<MonthTooltip />} cursor={{ fill: 'var(--surface-3)', opacity: 0.6 }} />
          <Legend itemSorter={null} />
          <Bar dataKey="income" name="Einnahmen" fill={INCOME_COLOR}>
            {history.map((m) => (
              <Cell key={m.month} fillOpacity={opacity(m.month)} />
            ))}
          </Bar>
          <Bar dataKey="expenses" name="Ausgaben" fill={EXPENSE_COLOR}>
            {history.map((m) => (
              <Cell key={m.month} fillOpacity={opacity(m.month)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

// --- Vorausschau ---

function Outlook() {
  const { data, isPending, error } = useReserves()
  if (isPending) {
    return <p>Lade Vorausschau …</p>
  }
  if (error) {
    return <p className="error">Vorausschau konnte nicht geladen werden: {error.message}</p>
  }
  const largest = Math.max(0, ...data.forecast.map((m) => m.total))
  return (
    <>
      <p className="hint">Ab {formatMonth(data.month)}, dem aktuellen Monat.</p>
      <h3 className="with-info">
        Rücklagen
        <InfoTip label="Rücklagen">
          Manche Ausgaben kommen nur alle 3, 6 oder 12 Monate, dann aber auf einmal. Legst du jeden Monat einen Teil
          zurück, ist das Geld da, wenn sie fällig werden. Die App rechnet aus, wie viel das ist; was du tatsächlich
          zurückgelegt hast, kennt sie nicht.
        </InfoTip>
      </h3>
      <ReservePot data={data} />

      <h3>Vorschau 12 Monate</h3>
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
    </>
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
          <dt className="with-info">
            Monatlich zurücklegen
            <InfoTip label="Monatlich zurücklegen">
              Die Summe der Monatsraten aller seltenen Ausgaben. Eine Rate ist der Betrag geteilt durch die Monate des
              Intervalls, z. B. 120 € jährlich = 10 € im Monat.
            </InfoTip>
          </dt>
          <dd>{formatEuro(data.monthlyReserve)}</dd>
        </div>
        <div>
          <dt className="with-info">
            Sollte jetzt angespart sein
            <InfoTip label="Sollte jetzt angespart sein">
              So viel sollte jetzt im Topf liegen, wenn du seit der letzten Abbuchung jeder Ausgabe jeden Monat ihre
              Rate zurückgelegt hast, die Rate dieses Monats eingeschlossen. Das ist ein Sollwert, kein Kontostand.
            </InfoTip>
          </dt>
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
            <th className="number">
              <span className="with-info">
                Pro Monat (€)
                <InfoTip label="Pro Monat">
                  Der Betrag geteilt durch die Monate des Intervalls: vierteljährlich 3, halbjährlich 6, jährlich 12.
                </InfoTip>
              </span>
            </th>
            <th className="number">
              <span className="with-info">
                Angespart (€)
                <InfoTip label="Angespart">
                  Die Raten seit der letzten Abbuchung, diesen Monat eingeschlossen. Im Monat der Fälligkeit ist der
                  volle Betrag angespart, danach beginnt es wieder bei einer Rate.
                </InfoTip>
              </span>
            </th>
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

      <h4 className="with-info">
        Verlauf des Topfs
        <InfoTip label="Verlauf des Topfs">
          Die Linie zeigt, wie viel in jedem Monat im Topf liegen sollte, bevor fällige Ausgaben abgebucht werden. Die
          Balken zeigen, was in dem Monat abgebucht wird. Danach fällt der Topf und wächst jeden Monat wieder um die
          Raten.
        </InfoTip>
      </h4>
      <PotChart data={data} />
    </>
  )
}

const POT_COLOR = 'var(--primary)'

interface PotPoint extends PotMonth {
  /** Rare expenses due in the month, from the forecast, for the tooltip only. */
  due: string[]
}

function PotTooltip({ active, payload }: { active?: boolean; payload?: { payload: PotPoint }[] }) {
  if (!active || !payload?.length) {
    return null
  }
  const point = payload[0].payload
  return (
    <div className="chart-tooltip">
      <strong>{formatMonth(point.month)}</strong>
      <span>Soll-Stand {formatEuro(point.balance)}</span>
      {point.withdrawal > 0 && (
        <span>
          Abbuchung {formatEuro(point.withdrawal)}
          {point.due.length > 0 && ` (${point.due.join(', ')})`}
        </span>
      )}
    </div>
  )
}

/** Decision 37: the balance of the pot as a line, the withdrawals as bars, 12 months from the current month. */
function PotChart({ data }: { data: ReserveData }) {
  const reserveIds = new Set(data.reserves.map((line) => line.recurringId))
  const points: PotPoint[] = data.potMonths.map((pot) => ({
    ...pot,
    due: (data.forecast.find((m) => m.month === pot.month)?.entries ?? [])
      .filter((entry) => entry.recurringId !== null && reserveIds.has(entry.recurringId))
      .map((entry) => entry.description),
  }))
  return (
    <div
      className="history-chart"
      role="img"
      aria-label="Diagramm Verlauf des Topfs: Soll-Stand und Abbuchungen je Monat"
    >
      <ResponsiveContainer width="100%" height={220}>
        <ComposedChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" tickFormatter={formatMonthShort} fontSize={12} />
          <YAxis tickFormatter={formatWholeNumber} fontSize={12} width={56} />
          <Tooltip content={<PotTooltip />} cursor={{ fill: 'var(--surface-3)', opacity: 0.6 }} />
          <Legend itemSorter={null} />
          <Bar dataKey="withdrawal" name="Abbuchung" fill={EXPENSE_COLOR} />
          <Line
            dataKey="balance"
            name="Soll-Stand"
            type="linear"
            stroke={POT_COLOR}
            strokeWidth={2}
            dot={{ r: 3, fill: POT_COLOR }}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
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
