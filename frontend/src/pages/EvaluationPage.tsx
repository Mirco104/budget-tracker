import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { errorText } from '../api/client'
import { useHistory, useReserves } from '../api/reserves'
import type { ForecastMonth, HistoryData, HistoryMonth, ReserveData, ReserveLine } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { InfoTip } from '../components/ui/InfoTip'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
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
  const [active, setActive] = useState<string | null>(null)
  if (isPending) {
    return <p>Lade Vorausschau …</p>
  }
  if (error) {
    return <p className="error">Vorausschau konnte nicht geladen werden: {error.message}</p>
  }
  return (
    <>
      <p className="hint">Ab {formatMonth(data.month)}, dem aktuellen Monat.</p>
      <h3 className="with-info">
        Rücklagen für seltene Ausgaben
        <InfoTip label="Rücklagen">
          Manche Ausgaben kommen nur alle 3, 6 oder 12 Monate, dann aber auf einmal. Legst du jeden Monat einen Teil
          zurück, ist das Geld da, wenn sie fällig werden. Die App rechnet aus, wie viel das ist; was du tatsächlich
          zurückgelegt hast, kennt sie nicht.
        </InfoTip>
      </h3>
      <Reserves data={data} />

      <h3 className="with-info">
        Vorschau 12 Monate
        <InfoTip label="Vorschau 12 Monate">
          Alle gebuchten Ausgaben je Monat, wiederkehrende und einzelne. Blau ist der Teil, den du aus deinen
          Rücklagen bezahlst: seltene Ausgaben, für die du jeden Monat etwas beiseitelegst.
        </InfoTip>
      </h3>
      <ForecastChart months={data.forecast} active={active} onActive={setActive} />
      <table className="history" aria-label="Vorschau 12 Monate">
        <thead>
          <tr>
            <th>Monat</th>
            <th className="number">Ausgaben (€)</th>
            <th className="number">davon aus den Rücklagen (€)</th>
          </tr>
        </thead>
        <tbody>
          {data.forecast.map((m) => (
            <ForecastRows key={m.month} month={m} active={active === m.month} onActive={setActive} />
          ))}
        </tbody>
      </table>
    </>
  )
}

/** Decision 37: the rate, the count and the next rare expense; the list is sorted by the due date. */
function reserveSentence(data: ReserveData) {
  const next = data.reserves[0]
  const count = data.reserves.length
  const nextText = `„${next.description}“ im ${formatMonth(next.nextDate.slice(0, 7))} (${formatEuro(next.amount)})`
  const paid = count === 1
    ? `Davon bezahlst du 1 seltene Ausgabe: ${nextText}.`
    : `Davon bezahlst du ${count} seltene Ausgaben, als Nächstes ${nextText}.`
  return `Leg jeden Monat ${formatEuro(data.monthlyReserve)} beiseite. ${paid}`
}

function ratesLeftText(line: ReserveLine) {
  if (line.ratesLeft === 0) {
    return 'diesen Monat fällig'
  }
  return `noch ${line.ratesLeft} ${line.ratesLeft === 1 ? 'Rate' : 'Raten'} à ${formatEuro(line.monthly)}`
}

function Reserves({ data }: { data: ReserveData }) {
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
      <p>{reserveSentence(data)}</p>
      <dl className="summary">
        <div>
          <dt className="with-info">
            Jeden Monat beiseitelegen
            <InfoTip label="Jeden Monat beiseitelegen">
              Die Summe der Monatsraten aller seltenen Ausgaben. Eine Rate ist der Betrag geteilt durch die Monate des
              Intervalls, z. B. 120 € jährlich = 10 € im Monat.
            </InfoTip>
          </dt>
          <dd>{formatEuro(data.monthlyReserve)}</dd>
        </div>
        <div>
          <dt className="with-info">
            Sollte zurückgelegt sein
            <InfoTip label="Sollte zurückgelegt sein">
              So viel sollte jetzt zurückgelegt sein, wenn du seit der letzten Abbuchung jeder Ausgabe jeden Monat ihre
              Rate beiseitegelegt hast, die Rate dieses Monats eingeschlossen. Das ist ein Sollwert, kein Kontostand.
            </InfoTip>
          </dt>
          <dd>{formatEuro(data.reserveBalance)}</dd>
          <dd className="tile-note">Summe der Balken unten</dd>
        </div>
      </dl>
      <ul className="reserve-list" aria-label="Seltene Ausgaben">
        {data.reserves.map((line) => (
          <li key={line.recurringId} className="reserve-item">
            <div className="reserve-head">
              <span>
                <strong>{line.description}</strong> <span className="badge">{INTERVAL_LABELS[line.interval]}</span>
              </span>
              <span className="tile-note">
                {formatEuro(line.amount)} · fällig am {formatDate(line.nextDate)}
              </span>
            </div>
            <ProgressBar value={line.amount > 0 ? line.saved / line.amount : 0} />
            <div className="reserve-foot">
              <span className="reserve-saved">
                {formatEuro(line.saved)} von {formatEuro(line.amount)}
              </span>
              <span className="tile-note reserve-rest">{ratesLeftText(line)}</span>
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}

const RESERVES_COLOR = 'var(--primary)'

/** Expanded: one row per expense with its date; an expense paid from the reserves also stands in that column. */
function ForecastRows({ month, active, onActive }: {
  month: ForecastMonth
  active: boolean
  onActive: (month: string | null) => void
}) {
  const [open, setOpen] = useState(false)
  const hasEntries = month.entries.length > 0
  const hover = {
    onMouseEnter: () => onActive(month.month),
    onMouseLeave: () => onActive(null),
  }
  const highlight = active ? ' highlighted' : ''
  return (
    <>
      <tr
        className={`history-month${hasEntries ? ' expandable' : ''}${highlight}`}
        onClick={hasEntries ? () => setOpen(!open) : undefined}
        {...hover}
      >
        <th scope="row">
          {hasEntries ? (
            <button type="button" aria-expanded={open}>
              <span aria-hidden="true">{open ? '▾' : '▸'}</span> <MonthLabel month={month.month} />
            </button>
          ) : (
            <MonthLabel month={month.month} />
          )}
        </th>
        <td className="number">{formatAmount(month.total)}</td>
        <td className={`number${month.fromReserves > 0 ? ' from-reserves' : ''}`}>
          {month.fromReserves > 0 ? formatAmount(month.fromReserves) : '–'}
        </td>
      </tr>
      {open &&
        month.entries.map((entry) => (
          <tr
            key={entry.kind === 'SINGLE' ? `single-${entry.id}` : `recurring-${entry.recurringId}-${entry.date}`}
            className={`history-category${highlight}`}
            {...hover}
          >
            <td>
              <span className="forecast-entry">
                <span className="forecast-date">{formatDate(entry.date)}</span>
                <span>{entry.description}</span>
                {entry.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
              </span>
            </td>
            <td className="number">{formatAmount(entry.amount)}</td>
            <td className="number from-reserves">{entry.fromReserves ? formatAmount(entry.amount) : ''}</td>
          </tr>
        ))}
    </>
  )
}

function ForecastTooltip({ active, payload }: { active?: boolean; payload?: { payload: ForecastMonth }[] }) {
  if (!active || !payload?.length) {
    return null
  }
  const month = payload[0].payload
  const names = month.entries.filter((entry) => entry.fromReserves).map((entry) => entry.description)
  return (
    <div className="chart-tooltip">
      <strong>{formatMonth(month.month)}</strong>
      <span>Ausgaben {formatEuro(month.total)}</span>
      {month.fromReserves > 0 && (
        <span>
          davon aus den Rücklagen {formatEuro(month.fromReserves)} ({names.join(', ')})
        </span>
      )}
    </div>
  )
}

/** Decision 37: the expenses per month, the part from the reserves in blue on top; the marked month is shared. */
function ForecastChart({ months, active, onActive }: {
  months: ForecastMonth[]
  active: string | null
  onActive: (month: string | null) => void
}) {
  const opacity = (month: string) => (active === null || active === month ? 1 : FADED_OPACITY)
  // Split for the stacked bars only; both values come from the backend
  const points = months.map((m) => ({ ...m, other: m.total - m.fromReserves }))
  return (
    <div
      className="history-chart"
      role="img"
      aria-label="Diagramm Vorschau: Ausgaben je Monat, der Teil aus den Rücklagen blau"
    >
      <ResponsiveContainer width="100%" height={220}>
        <BarChart
          data={points}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          onMouseMove={(state) => onActive(state.activeLabel === undefined ? null : String(state.activeLabel))}
          onMouseLeave={() => onActive(null)}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" tickFormatter={formatMonthShort} fontSize={12} />
          <YAxis tickFormatter={formatWholeNumber} fontSize={12} width={56} />
          <Tooltip content={<ForecastTooltip />} cursor={{ fill: 'var(--surface-3)', opacity: 0.6 }} />
          <Legend itemSorter={null} />
          <Bar dataKey="other" name="Ausgaben" stackId="month" fill={EXPENSE_COLOR}>
            {points.map((m) => (
              <Cell key={m.month} fillOpacity={opacity(m.month)} />
            ))}
          </Bar>
          <Bar dataKey="fromReserves" name="davon aus den Rücklagen" stackId="month" fill={RESERVES_COLOR}>
            {points.map((m) => (
              <Cell key={m.month} fillOpacity={opacity(m.month)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
