import { useState } from 'react'
import { Icon } from '../components/ui/Icon'
import { InfoTip } from '../components/ui/InfoTip'
import { EXPECTED_ROUTE, transactionsPeriodHref } from '../router'
import { useCategories } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import { usePlan } from '../api/plan'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
import { useTransactions } from '../api/transactions'
import type { CategoryLine, DashboardData, PlannedItem, ReservePot, Transaction } from '../api/types'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { useBookingDialog } from '../components/useBookingDialog'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { bookingDateFor, formatDate, formatEuro, formatMonthName, formatSigned } from '../format'
import { averagePeriod, rhythmLabel } from '../labels'
import { ROUTES } from '../router'
import { Loading } from '../components/ui/Loading'

type Line = Pick<CategoryLine, 'target' | 'actual' | 'difference'>

/** "2026-11" -> "2026-11-30" */
function lastDayOf(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return `${month}-${String(new Date(year, m, 0).getDate()).padStart(2, '0')}`
}

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
      <DashboardContent month={month} onEditBooking={booking.openEdit} />
      <AddBookingFab onClick={booking.openNew} />
      {booking.element}
    </section>
  )
}

function DashboardContent({ month, onEditBooking }: { month: string; onEditBooking: (entry: Transaction) => void }) {
  const { data, isPending, error } = useDashboard(month)
  const [incomeOpen, setIncomeOpen] = useState(false)
  // 27f4: the expenses open on the budget table by default
  const [expensesOpen, setExpensesOpen] = useState(true)
  const plan = usePlan(month)
  const [editItem, setEditItem] = useState<PlannedItem | null>(null)
  const openItem = (itemId: number) => {
    const found = plan.data?.items.find((item) => item.id === itemId)
    if (found) {
      setEditItem(found)
    }
  }
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

      <div className="summary-rows">
        <section className={incomeOpen ? 'summary-row open' : 'summary-row'} aria-label="Einnahmen">
          <button type="button" className="summary-head" aria-expanded={incomeOpen} onClick={() => setIncomeOpen((v) => !v)}>
            <span className="summary-label">Einnahmen</span>
            <span className="summary-value income">{formatEuro(data.incomeTotal)}</span>
            {data.averageIncome !== null && (
              <span className="summary-note">
                Ø {formatEuro(data.averageIncome)} · {averagePeriod(data)}
              </span>
            )}
            <span className="summary-chevron" aria-hidden="true">
              {incomeOpen ? '▴' : '▾'}
            </span>
          </button>
          {/* 27f2/27f3: the income per category of the month, only when the card is open */}
          {incomeOpen && (
            <div className="tile-list">
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
            </div>
          )}
        </section>
        <section className={expensesOpen ? 'summary-row open' : 'summary-row'} aria-label="Ausgaben">
          {/* 27f4: the card opens on the budget table; the head shows the real expenses (P1) */}
          <button
            type="button"
            className="summary-head"
            aria-expanded={expensesOpen}
            onClick={() => setExpensesOpen((v) => !v)}
          >
            <span className="summary-label">Ausgaben</span>
            <span className="summary-value">{formatEuro(data.expenses.actual)}</span>
            {data.averageExpenses !== null && (
              <span className="summary-note">
                Ø {formatEuro(data.averageExpenses)} · {averagePeriod(data)}
              </span>
            )}
            <span className="summary-chevron" aria-hidden="true">
              {expensesOpen ? '▴' : '▾'}
            </span>
          </button>
          {/* P5: an expense that fits a planned item is fix, every other one variable */}
          <p className="breakdown">
            <span>
              fix {formatEuro(data.expenses.recurring)} · variabel {formatEuro(data.expenses.oneOff)}
            </span>{' '}
            <InfoTip label="Was ist fix?">
              Fix sind regelmäßige Zahlungen, die du in der Planung eingetragen hast, z. B. Miete, Versicherungen, Abos.
              Alles andere ist variabel.
            </InfoTip>
          </p>
          {data.fromReserves != null && (
            <p className="breakdown">
              <span>davon aus Rücklagen bezahlt {formatEuro(data.fromReserves)}</span>{' '}
              <InfoTip label="Warum zählt das Budget weniger?">
                Die Budgets zählen für eine Zahlung aus der Rücklage nur die Monatsrate, die Ausgaben oben die ganze
                Zahlung.
              </InfoTip>
            </p>
          )}
          {expensesOpen && (
            <div className="summary-body">
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
                      onEditBooking={onEditBooking}
                      onOpenItem={openItem}
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
              <a className="summary-link" href={transactionsPeriodHref(`${month}-01`, lastDayOf(month))}>
                Alle Buchungen im {formatMonthName(month)} ›
              </a>
            </div>
          )}
        </section>
        {data.saving && <SavingCard saving={data.saving} onOpenItem={openItem} />}
      </div>

      {showOnboarding && <Onboarding />}


      {data.expected && (
        <a className="link-card" href={EXPECTED_ROUTE}>
          <span className="budget-name">Erwartet</span>
          <span className="budget-amount">
            <span>
              {data.expected.count === 1 ? '1 Zahlung' : `${data.expected.count} Zahlungen`} im {formatMonthName(month)}
            </span>
            {data.expected.count > 0 && <span className="budget-left">zusammen {formatSigned(data.expected.total)}</span>}
          </span>
          <span className="summary-chevron" aria-hidden="true">
            ›
          </span>
        </a>
      )}

      {editItem && categories.data && (
        <PlannedItemDialog
          categories={categories.data}
          item={editItem}
          month={month}
          onClose={() => setEditItem(null)}
          onDone={() => setEditItem(null)}
        />
      )}
    </>
  )
}

/**
 * P9, 27f3: what was saved in the month. 27f4: a card like Einnahmen and Ausgaben; its head shows saved of planned,
 * open one row per saving item with what its bookings brought this month and a bar. A row opens the saving item.
 */
function SavingCard({
  saving,
  onOpenItem,
}: {
  saving: NonNullable<DashboardData['saving']>
  onOpenItem: (itemId: number) => void
}) {
  const [open, setOpen] = useState(false)
  const actual = saving.actual ?? 0
  return (
    <section className={open ? 'summary-row open' : 'summary-row'} aria-label="Sparen">
      <button type="button" className="summary-head" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="summary-label">Sparen</span>
        <span className="summary-value saving">{formatEuro(actual)}</span>
        <span className="summary-note">
          von {formatEuro(saving.planned)} geplant
          {saving.rate !== null && ` · Sparquote ${saving.rate.toLocaleString('de-DE')} %`}
        </span>
        <span className="summary-chevron" aria-hidden="true">
          {open ? '▴' : '▾'}
        </span>
      </button>
      {open && (
        <div className="summary-body">
          {saving.items.length === 0 ? (
            <p className="hint">Noch keine Sparposten in der Planung.</p>
          ) : (
            <ul className="budget-list">
              {saving.items.map((item) => (
                <li key={item.itemId} className="budget-item">
                  <button type="button" className="budget-head budget-toggle" onClick={() => onOpenItem(item.itemId)}>
                    <span className="budget-name">{item.name}</span>
                    <span className="budget-amount">
                      <span className="saving">
                        {formatEuro(item.actual ?? 0)} von {formatEuro(item.monthly)}
                      </span>
                    </span>
                  </button>
                  <div className="saving-progress">
                    <ProgressBar value={item.monthly > 0 ? (item.actual ?? 0) / item.monthly : 0} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
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
  onEditBooking,
  onOpenItem,
}: {
  line: CategoryLine
  icon: string
  month: string
  pots: ReservePot[]
  onEditBooking: (entry: Transaction) => void
  onOpenItem: (itemId: number) => void
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
      {open && (
        <CategoryBookings
          month={month}
          categoryId={line.id}
          pots={pots}
          onEditBooking={onEditBooking}
          onOpenItem={onOpenItem}
        />
      )}
    </li>
  )
}

/** US12: single transactions and occurrences of recurring ones that count in the category in the month. */
function CategoryBookings({
  month,
  categoryId,
  pots,
  onEditBooking,
  onOpenItem,
}: {
  month: string
  categoryId: number
  pots: ReservePot[]
  onEditBooking: (entry: Transaction) => void
  onOpenItem: (itemId: number) => void
}) {
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
        <ReserveRow key={pot.itemId} pot={pot} onOpen={() => onOpenItem(pot.itemId)} />
      ))}
      {entries.map((entry) => (
        <li key={entry.id}>
          <button type="button" className="booking-row" onClick={() => onEditBooking(entry)}>
          <span className="booking-date">{formatDate(entry.date)}</span>
          <span className="booking-description">
            {entry.description}
            {entry.fix && <span className="badge">fix</span>}
            {/* 27f4 (decision 60): the payment itself says where its money came from */}
            {entry.reserveItemKey && <span className="badge">aus Rücklage</span>}
            {entry.savingGoalId != null && <span className="badge">aus Sparziel</span>}
          </span>
          <span className={`booking-amount${entry.amount < 0 ? '' : ' positive'}`}>{formatEuro(entry.amount)}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

/**
 * P8, 27f2: the reserve of a rare item in the open budget row, shown like a transfer. 27f4 (decision 60): it always
 * shows the monthly share that counts in the budget; the payment is its own booking marked "aus Rücklage" (orange
 * when the pot was too small).
 */
function ReserveRow({ pot, onOpen }: { pot: ReservePot; onOpen: () => void }) {
  const short = pot.shortfall > 0
  const paid = pot.paid > 0
  const due = pot.nextDue && !paid ? ` · fällig im ${formatMonthName(pot.nextDue.slice(0, 7))}` : ''
  return (
    <li className={short ? 'reserve-row short' : 'reserve-row'}>
      <button type="button" className="booking-row" onClick={onOpen}>
        <span className="booking-date" aria-hidden="true">
          <Icon name="transfer" size={14} />
        </span>
        <span className="booking-description">
          Rücklage {pot.name}
          <span className="tx-meta">
            zurückgelegt für {formatEuro(pot.amount)} {rhythmLabel('MONTH', pot.months)}
            {due}
            {paid && ` · Zahlung ${formatEuro(pot.paid)} aus der Rücklage`}
            {short && ` · ${formatEuro(pot.shortfall)} fehlten darin`}
          </span>
        </span>
        {/* 27f4: grey like a transfer; green would read as income */}
        <span className="booking-amount transfer">{formatEuro(pot.monthly)}</span>
      </button>
      <InfoTip label="Wie rechnet die Rücklage?">
        Monatsrate = Betrag ÷ Monate des Rhythmus ({formatEuro(pot.amount)} ÷ {pot.months} = {formatEuro(pot.monthly)}).
        Im Budget zählt jeden Monat nur diese Rate. Die Zahlung selbst steht als Buchung mit „aus Rücklage“ darunter
        und zählt nicht noch einmal im Budget; fehlt in der Rücklage etwas, zählt der Rest als Ausgabe des Monats.
      </InfoTip>
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
