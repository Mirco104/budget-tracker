import { useState } from 'react'
import { useCategories } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import { useTransactions } from '../api/transactions'
import type { CategoryLine, DashboardData } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { formatDate, formatEuro, formatPercent } from '../format'
import { averagePeriod } from '../labels'
import { ROUTES } from '../router'

type Unit = 'euro' | 'percent'

type Line = Pick<CategoryLine, 'target' | 'actual' | 'difference' | 'targetShare' | 'actualShare' | 'differenceShare'>

const MONTH_NOTES: Record<DashboardData['monthState'], string> = {
  PAST: 'Abgeschlossener Monat.',
  CURRENT: 'Laufender Monat: die Werte können sich noch ändern.',
  FUTURE: 'Künftiger Monat: es zählen nur wiederkehrende und vorgemerkte Einträge.',
}

function signClass(value: number | null): string | undefined {
  return value !== null && value < 0 ? 'negative' : undefined
}

/** The values of a line in the chosen unit; shares are null without income and show as a dash. */
function valuesOf(line: Line, unit: Unit) {
  return unit === 'euro'
    ? { target: line.target, actual: line.actual, difference: line.difference }
    : { target: line.targetShare, actual: line.actualShare, difference: line.differenceShare }
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
  // The dashboard lines carry no icon; the categories do (US28)
  const categories = useCategories(month)

  if (isPending) {
    return <p>Lade Übersicht …</p>
  }
  if (error) {
    return <p className="error">Dashboard konnte nicht geladen werden: {error.message}</p>
  }

  const format = (amount: number | null, share: number | null) =>
    unit === 'euro' ? formatEuro(amount) : formatPercent(share)
  const iconOf = (id: number) => categories.data?.find((category) => category.id === id)?.icon ?? 'tag'

  // US5: nothing booked and planned yet. Only the current month counts, an empty past month is just empty.
  const hasTarget = categories.data?.some((c) => c.targetAmount !== null || c.targetPercent !== null) ?? true
  const showOnboarding =
    data.monthState === 'CURRENT' && data.categories.length === 0 && data.income.length === 0 && !hasTarget

  return (
    <>
      <p className="hint">{MONTH_NOTES[data.monthState]}</p>

      <dl className="summary">
        <div>
          <dt>Einnahmen</dt>
          <dd>{format(data.incomeTotal, data.incomeTotalShare)}</dd>
          {/* R23: the Ø of the months before is the basis of the percent targets */}
          {data.averageIncome !== null && (
            <dd className="tile-note">
              Ø {format(data.averageIncome, data.averageIncomeShare)} · {averagePeriod(data)}
            </dd>
          )}
        </div>
        <div>
          <dt>Ausgaben</dt>
          <dd>{format(data.expenses.actual, data.expenses.actualShare)}</dd>
          {/* R10: recurring transactions are the fixed costs, single transactions the rest */}
          <dd className="breakdown">
            davon wiederkehrend <span>{format(data.expenses.recurring, data.expenses.recurringShare)}</span>
          </dd>
          <dd className="breakdown">
            davon einmalig <span>{format(data.expenses.oneOff, data.expenses.oneOffShare)}</span>
          </dd>
        </div>
        <div>
          <dt>{data.surplusLabel}</dt>
          <dd className={signClass(data.surplus)}>{format(data.surplus, data.surplusShare)}</dd>
        </div>
      </dl>

      {showOnboarding && <Onboarding />}

      <h3>Budget</h3>
      {data.categories.length === 0 ? (
        <p className="hint">In diesem Monat gibt es weder Soll-Werte noch Ausgaben.</p>
      ) : (
        <ul className="budget-list">
          {data.categories.map((line) => (
            <BudgetItem key={line.id} line={line} icon={iconOf(line.id)} unit={unit} month={month} />
          ))}
          <li className="budget-item budget-total">
            <div className="budget-head">
              <span className="budget-name">Gesamt</span>
              <BudgetNumbers line={data.expenses} unit={unit} />
            </div>
          </li>
        </ul>
      )}

      <h3>Einnahmen</h3>
      {data.income.length === 0 ? (
        <p className="hint">In diesem Monat gibt es keine Einnahmen.</p>
      ) : (
        <ul className="budget-list">
          {data.income.map((line) => (
            <li key={line.id} className="budget-item">
              <div className="budget-head">
                <CategoryIcon icon={iconOf(line.id)} />
                <span className="budget-name">{line.name}</span>
                <span className="budget-amount income">{format(line.actual, line.share)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/** "Ist von Soll" and what is left or exceeded (R5); a line without target says so and has no bar. */
function BudgetNumbers({ line, unit }: { line: Line; unit: Unit }) {
  const { target, actual, difference } = valuesOf(line, unit)
  const format = unit === 'euro' ? formatEuro : formatPercent
  const hasTarget = line.target !== null
  const over = line.difference !== null && line.difference < 0

  return (
    <span className="budget-amount">
      <span>
        {format(actual)}
        {hasTarget ? ` von ${format(target)}` : ' · kein Soll'}
      </span>
      {hasTarget && (
        <span className={`budget-left${over ? ' over' : ''}`}>
          {difference === null ? '–' : over ? `${format(-difference)} über dem Soll` : `noch ${format(difference)}`}
        </span>
      )}
    </span>
  )
}

function BudgetItem({
  line,
  icon,
  unit,
  month,
}: {
  line: CategoryLine
  icon: string
  unit: Unit
  month: string
}) {
  const [open, setOpen] = useState(false)
  const over = line.difference !== null && line.difference < 0
  // Display only: how much of the target is used (the backend calculated all amounts)
  const used = line.target !== null && line.target > 0 ? line.actual / line.target : line.actual > 0 ? 1 : 0

  return (
    <li className="budget-item">
      <button
        type="button"
        className="budget-head budget-toggle"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <CategoryIcon icon={icon} />
        <span className="budget-name">{line.name}</span>
        <BudgetNumbers line={line} unit={unit} />
      </button>
      {line.target !== null && <ProgressBar value={used} over={over} />}
      {open && <CategoryBookings month={month} categoryId={line.id} />}
    </li>
  )
}

/** US12: single transactions and occurrences of recurring ones that count in the category in the month. */
function CategoryBookings({ month, categoryId }: { month: string; categoryId: number }) {
  const { data, isPending, error } = useTransactions(month)

  if (isPending) {
    return <p className="hint">Lade Buchungen …</p>
  }
  if (error) {
    return <p className="error">Buchungen konnten nicht geladen werden: {error.message}</p>
  }
  const entries = data.filter((entry) => entry.categoryId === categoryId)
  if (entries.length === 0) {
    return <p className="hint">Keine Buchungen in diesem Monat.</p>
  }
  return (
    <ul className="bookings">
      {entries.map((entry) => (
        <li key={entry.kind === 'SINGLE' ? `single-${entry.id}` : `recurring-${entry.recurringId}-${entry.occurrenceDate}`}>
          <span className="booking-date">{formatDate(entry.date)}</span>
          <span className="booking-description">
            {entry.description}
            {entry.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
          </span>
          <span className={`booking-amount${entry.amount < 0 ? '' : ' positive'}`}>{formatEuro(entry.amount)}</span>
        </li>
      ))}
    </ul>
  )
}

/** Three steps for a user who has not entered anything yet (US5). */
function Onboarding() {
  return (
    <section className="onboarding" aria-label="Erste Schritte">
      <h3>So fängst du an</h3>
      <ol>
        <li>
          <a href={ROUTES.planning}>Soll festlegen</a>: wie viel darf jede Kategorie im Monat kosten?
        </li>
        <li>
          <a href={ROUTES.transactions}>Erste Buchung erfassen</a>: Einnahmen und Ausgaben eintragen.
        </li>
        <li>Hier die Übersicht ansehen: du siehst sofort, was noch frei ist.</li>
      </ol>
    </section>
  )
}
