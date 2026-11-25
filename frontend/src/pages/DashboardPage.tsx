import { ClosedMonthCard } from '../components/ClosedMonthCard'
import { useState } from 'react'
import { Icon } from '../components/ui/Icon'
import { InfoTip } from '../components/ui/InfoTip'
import { EXPECTED_ROUTE, transactionsPeriodHref } from '../router'
import { useCategories } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import { usePlan } from '../api/plan'
import { useMe } from '../api/auth'
import { useVariableFromBalance, type VariableFromBalance as VariableData } from '../api/accounts'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
import { useTransactions } from '../api/transactions'
import type { CategoryLine, DashboardData, IstStage, Plan, PlannedItem, ReservePot, Transaction } from '../api/types'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { useBookingDialog } from '../components/useBookingDialog'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { ToggleAll } from '../components/ui/ToggleAll'
import { useOpenRows } from '../components/ui/useOpenRows'
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
  // US43: the overview shows what the Ist stage delivers (R35)
  const stage = useMe().data?.istStage ?? 4
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
        {stage > 1 && <AddBookingButton onClick={booking.openNew} />}
      </PageHeader>
      <DashboardContent month={month} stage={stage} onEditBooking={booking.openEdit} />
      {stage > 1 && <AddBookingFab onClick={booking.openNew} />}
      {booking.element}
    </section>
  )
}

function DashboardContent({
  month,
  stage,
  onEditBooking,
}: {
  month: string
  stage: IstStage
  onEditBooking: (entry: Transaction) => void
}) {
  const { data, isPending, error } = useDashboard(month)
  const variable = useVariableFromBalance(month, stage === 2)
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
  // Ticket #138: the budget rows that open with their bookings (the row "Ohne Kategorie" does not open)
  const budgetKeys = (data?.categories ?? []).flatMap((line) => (line.id === null ? [] : [String(line.id)]))
  const budgetRows = useOpenRows(budgetKeys)

  if (isPending) {
    return <Loading text="Lade Übersicht …" />
  }
  if (error) {
    return <p className="error">Übersicht konnte nicht geladen werden: {error.message}</p>
  }

  const iconOf = (id: number) => categories.data?.find((category) => category.id === id)?.icon ?? 'tag'

  // Stage 1: only the plan of the month, no actuals (US43)
  if (stage === 1) {
    return plan.data ? <PlanOnly plan={plan.data} iconOf={iconOf} /> : <Loading text="Lade Plan …" />
  }

  // US5: nothing booked and planned yet. Only the current month counts, an empty past month is just empty.
  const hasTarget = data.categories.some((c) => c.target !== null)
  const showOnboarding =
    data.monthState === 'CURRENT' && data.categories.length === 0 && data.income.length === 0 && !hasTarget

  return (
    <>
      <p className="hint">{MONTH_NOTES[data.monthState]}</p>

      <div className="summary-rows">
        {/* 27h, decision 59: a past month shows its surplus or shortfall first */}
        {data.monthState === 'PAST' && <ClosedMonthCard month={month} />}
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
                    <li key={line.id ?? 'without'}>
                      <CategoryIcon icon={line.id === null ? 'tag' : iconOf(line.id)} size={14} />
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
          {expensesOpen && stage === 2 && (
            <div className="summary-body">
              <VariableFromBalance month={month} data={variable.data} budgets={plan.data?.budgets ?? null} />
            </div>
          )}
          {expensesOpen && stage === 3 && (
            <div className="summary-body">
              <ul className="budget-list">
                <li className="budget-item budget-total">
                  <div className="budget-head">
                    <span className="budget-name">Alle Budgets zusammen</span>
                    <BudgetNumbers line={allBudgets(data.expenses.oneOff, plan.data?.budgets ?? null)} />
                  </div>
                  <ProgressBar
                    value={plan.data?.budgets ? data.expenses.oneOff / plan.data.budgets : 0}
                    over={plan.data !== undefined && data.expenses.oneOff > plan.data.budgets}
                  />
                </li>
              </ul>
              <p className="hint">Einzelne Budgets siehst du in Stufe 4, wenn du deinen Ausgaben Kategorien gibst.</p>
              <a className="summary-link" href={transactionsPeriodHref(`${month}-01`, lastDayOf(month))}>
                Alle Buchungen im {formatMonthName(month)} ›
              </a>
            </div>
          )}
          {expensesOpen && stage === 4 && (
            <div className="summary-body">
              {data.categories.length === 0 ? (
                <p className="hint">In diesem Monat gibt es weder Soll-Werte noch Ausgaben.</p>
              ) : (
                <>
                <ToggleAll count={budgetKeys.length} allOpen={budgetRows.allOpen} onToggle={budgetRows.toggleAll} />
                <ul className="budget-list">
                  {data.categories.map((line) =>
                    line.id === null ? (
                      <WithoutCategoryRow key="without" line={line} month={month} />
                    ) : (
                    <BudgetItem
                      key={line.id}
                      line={line}
                      icon={iconOf(line.id)}
                      month={month}
                      pots={(data.reserves ?? []).filter((pot) => pot.categoryId === line.id)}
                      open={budgetRows.isOpen(String(line.id))}
                      onToggle={() => budgetRows.toggle(String(line.id))}
                      onEditBooking={onEditBooking}
                      onOpenItem={openItem}
                    />
                    ),
                  )}
                  <li className="budget-item budget-total">
                    <div className="budget-head">
                      <span className="budget-name">Gesamt</span>
                      <BudgetNumbers line={data.budget ?? data.expenses} />
                    </div>
                  </li>
                </ul>
                </>
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
  open,
  onToggle,
  onEditBooking,
  onOpenItem,
}: {
  line: CategoryLine
  icon: string
  month: string
  pots: ReservePot[]
  open: boolean
  onToggle: () => void
  onEditBooking: (entry: Transaction) => void
  onOpenItem: (itemId: number) => void
}) {
  const over = line.difference !== null && line.difference < 0
  // Display only: how much of the target is used (the backend calculated all amounts)
  const used = line.target !== null && line.target > 0 ? line.actual / line.target : line.actual > 0 ? 1 : 0

  return (
    <li className="budget-item">
      <button
        type="button"
        className="budget-head budget-toggle"
        aria-expanded={open}
        onClick={onToggle}
      >
        <CategoryIcon icon={icon} />
        <span className="budget-name">{line.name}</span>
        <BudgetNumbers line={line} />
      </button>
      {line.target !== null && <ProgressBar value={used} over={over} />}
      {open && (
        <CategoryBookings
          month={month}
          categoryId={line.id ?? 0}
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


/** Stage 3: the variable expenses (P5) against the sum of the budgets (P4); only display arithmetic. */
function allBudgets(actual: number, target: number | null): Line {
  return { actual, target, difference: target === null ? null : target - actual }
}

/** R36, US43: stage 2 shows the variable spending from the balance on the last day of the month. */
function VariableFromBalance({ month, data, budgets }: { month: string; data: VariableData | undefined; budgets: number | null }) {
  if (!data || data.amount === null) {
    // R36: the very first balance is the start; the balance at the end of the month shows the variable spending
    if (data?.start) {
      return (
        <p className="hint">
          Dein Kontostand vom {formatDate(data.start)} ist der Start. Trag am Monatsende den nächsten auf der Seite{' '}
          <a href={ROUTES.accounts}>Konten</a> ein, dann siehst du, was du im {formatMonthName(month)} variabel ausgegeben hast.
        </p>
      )
    }
    return (
      <p className="hint">
        Trag am Monatsende den Kontostand auf der Seite <a href={ROUTES.accounts}>Konten</a> ein, dann siehst du, was du im{' '}
        {formatMonthName(month)} variabel ausgegeben hast. Fixkosten und Einkommen trägt die App selbst ein.
      </p>
    )
  }
  if (data.moreThanExpected) {
    return (
      <p className="hint">
        Auf dem Konto waren am {formatDate(data.date as string)} <strong>{formatEuro(data.amount)} mehr als erwartet</strong>, z. B. durch
        eine Erstattung. Variabel ausgegeben: {formatEuro(0)}.
      </p>
    )
  }
  return (
    <ul className="budget-list">
      <li className="budget-item budget-total">
        <div className="budget-head">
          <span className="budget-name">Variabel ausgegeben</span>
          <BudgetNumbers line={allBudgets(data.amount, budgets)} />
        </div>
        <ProgressBar value={budgets ? data.amount / budgets : 0} over={budgets !== null && data.amount > budgets} />
        <p className="tx-meta">
          {/* Review 23.11.2026: a period that starts within the month is only part of it */}
          {data.from && data.from.startsWith(month) && <strong>Teilmonat seit {formatDate(data.from)}: </strong>}
          {data.from ? `Seit dem Kontostand am ${formatDate(data.from)} bis zum ${formatDate(data.date as string)}` : `Bis zum Kontostand am ${formatDate(data.date as string)}`}
          , gegen die Summe deiner Budgets.
        </p>
      </li>
    </ul>
  )
}

/** Stage 1, US43: the plan of the month without actuals. */
function PlanOnly({ plan, iconOf }: { plan: Plan; iconOf: (id: number) => string }) {
  const budgets = plan.categories.filter((line) => line.group === 'BUDGET')
  return (
    <>
      <p className="hint">
        Du verfolgst nur den Plan. Willst du sehen, was du wirklich ausgibst? <a href="#/einstellungen/einrichtung">Stufe ändern</a>
      </p>
      <dl className="card setup-sum" aria-label="Plan des Monats">
        <div>
          <dt>Einkommen</dt>
          <dd className="income">{formatEuro(plan.income)}</dd>
        </div>
        <div>
          <dt>Fixkosten (Monatsanteile)</dt>
          <dd>{formatEuro(plan.fixed)}</dd>
        </div>
        <div>
          <dt>Budgets</dt>
          <dd>{formatEuro(plan.budgets)}</dd>
        </div>
        <div>
          <dt>
            Sparen
            {plan.savingRate !== null && <span className="tx-meta"> (Sparquote {plan.savingRate.toLocaleString('de-DE')} %)</span>}
          </dt>
          <dd className="saving">{formatEuro(plan.saving)}</dd>
        </div>
        <div className={plan.unplanned < 0 ? 'total over' : 'total'}>
          <dt>{plan.unplanned < 0 ? 'Überplant' : 'Nicht verplant'}</dt>
          <dd>{formatEuro(Math.abs(plan.unplanned))}</dd>
        </div>
      </dl>
      {budgets.length > 0 && (
        <section className="card" aria-label="Budgets im Monat">
          <h2>Budgets im Monat</h2>
          <ul className="budget-list">
            {budgets.map((line) => (
              <li key={line.categoryId} className="budget-item">
                <div className="budget-head">
                  <CategoryIcon icon={iconOf(line.categoryId)} />
                  <span className="budget-name">{line.categoryName}</span>
                  <span className="budget-amount">{formatEuro(line.total)}</span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}

/** R37: the expenses without a category; they count as variable, but in no budget. */
function WithoutCategoryRow({ line, month }: { line: CategoryLine; month: string }) {
  return (
    <li className="budget-item budget-without">
      <div className="budget-head">
        <span className="budget-name">Ohne Kategorie</span>
        <span className="budget-amount">
          <span>{formatEuro(line.actual)}</span>
          <span className="budget-left">zählt zu den variablen Kosten, aber zu keinem Budget</span>
        </span>
      </div>
      <a className="summary-link" href={transactionsPeriodHref(`${month}-01`, lastDayOf(month))}>
        Zuordnen ›
      </a>
    </li>
  )
}
