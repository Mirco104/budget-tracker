import { useState } from 'react'
import { Icon } from '../components/ui/Icon'
import { InfoTip } from '../components/ui/InfoTip'
import { EXPECTED_ROUTE } from '../router'
import { useCategories } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import { useTransactions } from '../api/transactions'
import type { CategoryLine, DashboardData, ReservePot } from '../api/types'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { useBookingDialog } from '../components/useBookingDialog'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { bookingDateFor, formatDate, formatEuro, formatMonthName, formatSigned } from '../format'
import { averagePeriod } from '../labels'
import { ROUTES } from '../router'
import { Loading } from '../components/ui/Loading'

type Line = Pick<CategoryLine, 'target' | 'actual' | 'difference'>

const MONTH_NOTES: Record<DashboardData['monthState'], string> = {
  PAST: 'Abgeschlossener Monat.',
  CURRENT: 'Laufender Monat: die Werte können sich noch ändern.',
  FUTURE: 'Künftiger Monat: es zählen nur schon gebuchte Einträge.',
}

interface Props {
  month: string
  /** Shows the month navigation in the page header (US8). */
  onMonthChange?: (month: string) => void
}

export function DashboardPage({ month, onMonthChange }: Props) {
  const categories = useCategories(month)
  // US27: "+ Buchung" also on the overview, where one checks what is left
  const booking = useBookingDialog({
    categories: categories.data,
    defaultDate: bookingDateFor(month),
  })

  return (
    <section>
      <PageHeader title="Übersicht">
        {onMonthChange && <MonthNav month={month} onChange={onMonthChange} />}
        <AddBookingButton onClick={booking.openNew} />
      </PageHeader>
      <DashboardContent month={month} />
      <AddBookingFab onClick={booking.openNew} />
      {booking.element}
    </section>
  )
}

function DashboardContent({ month }: { month: string }) {
  const { data, isPending, error } = useDashboard(month)
  const [incomeOpen, setIncomeOpen] = useState(false)
  // The dashboard lines carry no icon; the categories do (US28)
  const categories = useCategories(month)

  if (isPending) {
    return <Loading text="Lade Übersicht …" />
  }
  if (error) {
    return <p className="error">Übersicht konnte nicht geladen werden: {error.message}</p>
  }

  const iconOf = (id: number) => categories.data?.find((category) => category.id === id)?.icon ?? 'tag'

  // US5: nothing booked and planned yet. Only the current month counts, an empty past month is just empty.
  const hasTarget = data.categories.some((c) => c.target !== null)
  const showOnboarding =
    data.monthState === 'CURRENT' && data.categories.length === 0 && data.income.length === 0 && !hasTarget

  return (
    <>
      <p className="hint">{MONTH_NOTES[data.monthState]}</p>

      <dl className="summary">
        <div>
          <dt>
            <button
              type="button"
              className="tile-toggle"
              aria-expanded={incomeOpen}
              onClick={() => setIncomeOpen((value) => !value)}
            >
              Einnahmen <span aria-hidden="true">{incomeOpen ? '▴' : '▾'}</span>
            </button>
          </dt>
          <dd>{formatEuro(data.incomeTotal)}</dd>
          {/* 27f2: the income per category of the month, only when the tile is open */}
          {incomeOpen && (
            <dd className="tile-list">
              {data.income.length === 0 ? (
                <span className="hint">Keine Einnahmen in diesem Monat.</span>
              ) : (
                <ul>
                  {data.income.map((line) => (
                    <li key={line.id}>
                      <CategoryIcon icon={iconOf(line.id)} size={14} />
                      <span>{line.name}</span>
                      <span className="income">{formatEuro(line.actual)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </dd>
          )}
          {/* R23: the Ø of the months before is the basis of the percent targets */}
          {data.averageIncome !== null && (
            <dd className="tile-note">
              Ø {formatEuro(data.averageIncome)} · {averagePeriod(data)}
            </dd>
          )}
        </div>
        <div>
          <dt>Ausgaben</dt>
          <dd>{formatEuro(data.expenses.actual)}</dd>
          {/* P5: an expense that fits a planned item is fix, every other one variable */}
          <dd className="breakdown">
            <span>
              fix {formatEuro(data.expenses.recurring)} · variabel {formatEuro(data.expenses.oneOff)}
            </span>{' '}
            <InfoTip label="Was ist fix?">
              Fix sind regelmäßige Zahlungen, die du in der Planung eingetragen hast, z. B. Miete, Versicherungen, Abos. Alles andere ist variabel.
            </InfoTip>
          </dd>
          {/* R23, decision 34: Ā over the same months as the Ø income */}
          {data.averageExpenses !== null && (
            <dd className="tile-note">
              Ø {formatEuro(data.averageExpenses)} · {averagePeriod(data)}
            </dd>
          )}
        </div>
      </dl>

      {showOnboarding && <Onboarding />}

      <h3>Budget</h3>
      {data.categories.length === 0 ? (
        <p className="hint">In diesem Monat gibt es weder Soll-Werte noch Ausgaben.</p>
      ) : (
        <ul className="budget-list">
          {data.categories.map((line) => (
            <BudgetItem
              key={line.id}
              line={line}
              icon={iconOf(line.id)}
              month={month}
              pots={(data.reserves ?? []).filter((pot) => pot.categoryId === line.id)}
            />
          ))}
          <li className="budget-item budget-total">
            <div className="budget-head">
              <span className="budget-name">Gesamt</span>
              <BudgetNumbers line={data.budget ?? data.expenses} />
            </div>
          </li>
        </ul>
      )}

      {data.saving && <SavingCard saving={data.saving} />}

      {data.expected && (
        <a className="budget-item expected-card" href={EXPECTED_ROUTE}>
          <span className="budget-name">Erwartet</span>
          <span className="budget-amount">
            <span>
              {data.expected.count === 1 ? '1 Zahlung' : `${data.expected.count} Zahlungen`} im {formatMonthName(month)}
            </span>
            {data.expected.count > 0 && <span className="budget-left">zusammen {formatSigned(data.expected.total)}</span>}
          </span>
        </a>
      )}
    </>
  )
}

/** P9, 27f2: planned and saved this month; open it shows the saving items with their monthly share. */
function SavingCard({ saving }: { saving: NonNullable<DashboardData['saving']> }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="budget-item saving-card">
      <button type="button" className="budget-head budget-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="budget-name">Sparen</span>
        <span className="budget-amount">
          <span>
            {formatEuro(saving.actual ?? 0)} von {formatEuro(saving.planned)}
          </span>
          {saving.rate !== null && (
            <span className="budget-left">Sparquote {saving.rate.toLocaleString('de-DE')} %</span>
          )}
        </span>
      </button>
      {open && (
        <ul className="bookings">
          {saving.items.length === 0 && <li className="hint">Noch keine Sparposten in der Planung.</li>}
          {saving.items.map((item) => (
            <li key={item.name}>
              <span className="booking-description">{item.name}</span>
              <span className="booking-amount saving">{formatEuro(item.monthly)} im Monat</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** "Ist von Soll" and what is left or exceeded (R5); a line without target says so and has no bar. */
function BudgetNumbers({ line }: { line: Line }) {
  const { target, actual, difference } = line
  const over = difference !== null && difference < 0

  return (
    <span className="budget-amount">
      <span>
        {formatEuro(actual)}
        {target !== null ? ` von ${formatEuro(target)}` : ' · kein Soll'}
      </span>
      {difference !== null && (
        <span className={`budget-left${over ? ' over' : ''}`}>
          {over ? `${formatEuro(-difference)} über dem Soll` : `noch ${formatEuro(difference)}`}
        </span>
      )}
    </span>
  )
}

function BudgetItem({
  line,
  icon,
  month,
  pots,
}: {
  line: CategoryLine
  icon: string
  month: string
  pots: ReservePot[]
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
        <BudgetNumbers line={line} />
      </button>
      {line.target !== null && <ProgressBar value={used} over={over} />}
      {open && <CategoryBookings month={month} categoryId={line.id} pots={pots} />}
    </li>
  )
}

/** US12: single transactions and occurrences of recurring ones that count in the category in the month. */
function CategoryBookings({ month, categoryId, pots }: { month: string; categoryId: number; pots: ReservePot[] }) {
  const { data, isPending, error } = useTransactions(month)

  if (isPending) {
    return <Loading text="Lade Buchungen …" inline />
  }
  if (error) {
    return <p className="error">Buchungen konnten nicht geladen werden: {error.message}</p>
  }
  const entries = data.filter((entry) => entry.categoryId === categoryId)
  if (entries.length === 0 && pots.length === 0) {
    return <p className="hint">Keine Buchungen in diesem Monat.</p>
  }
  return (
    <ul className="bookings">
      {pots.map((pot) => (
        <ReserveRow key={pot.itemId} pot={pot} />
      ))}
      {entries.map((entry) => (
        <li key={entry.id}>
          <span className="booking-date">{formatDate(entry.date)}</span>
          <span className="booking-description">
            {entry.description}
            {entry.fix && <span className="badge">fix</span>}
          </span>
          <span className={`booking-amount${entry.amount < 0 ? '' : ' positive'}`}>{formatEuro(entry.amount)}</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * P8, 27f2: the reserve of a rare item in the open budget row, shown like a transfer: put aside this month, or in
 * the due month paid from the pot (orange when the pot was too small). It is no booking.
 */
function ReserveRow({ pot }: { pot: ReservePot }) {
  const short = pot.shortfall > 0
  const paid = pot.paid > 0
  return (
    <li className={short ? 'reserve-row short' : 'reserve-row'}>
      <span className="booking-date" aria-hidden="true">
        <Icon name="transfer" size={14} />
      </span>
      <span className="booking-description">
        Rücklage {pot.name}
        <span className="tx-meta">
          {paid
            ? short
              ? `${formatEuro(pot.shortfall)} fehlten in der Rücklage`
              : 'aus der Rücklage bezahlt'
            : `zurückgelegt · Stand ${formatEuro(pot.balance)} von ${formatEuro(pot.amount)}`}{' '}
          · keine Buchung
        </span>
      </span>
      <span className="booking-amount saving">{formatEuro(paid ? pot.fromPot : pot.monthly)}</span>
    </li>
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
