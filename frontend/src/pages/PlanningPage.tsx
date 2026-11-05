import { useState } from 'react'
import { useCategories, useUpdateTarget } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import type { Category, DashboardData } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { currentMonth, formatEuro, formatMonth, shiftMonth } from '../format'
import { averagePeriod } from '../labels'

/**
 * Bar that stays at the top while scrolling (US7a): Ø income (R23), planned targets and the unplanned rest (R4)
 * for the month "Gilt ab", taken from the dashboard. It refreshes after every saved target.
 */
function PlanningBar({ data }: { data: DashboardData | undefined }) {
  if (!data) {
    return null
  }
  if (data.averageIncome === null || data.unplanned === null) {
    return (
      <div className="plan-bar">
        <p className="hint">
          Noch kein abgeschlossener Monat mit Einträgen. Prozent-Soll zählen erst, wenn es einen gibt, denn sie beziehen
          sich auf die durchschnittlichen Einnahmen der Monate davor.
        </p>
      </div>
    )
  }
  const overPlanned = data.unplanned < 0
  return (
    <dl className="plan-bar summary">
      <div>
        <dt>Ø Einnahmen</dt>
        <dd>{formatEuro(data.averageIncome)}</dd>
        <dd className="tile-note">{averagePeriod(data)}</dd>
      </div>
      <div>
        <dt>Verplant</dt>
        <dd>{formatEuro(data.expenses.target)}</dd>
      </div>
      <div>
        <dt>{data.unplannedLabel}</dt>
        {/* The label already says "Überplant", so the amount is shown without minus sign in the warning color (R4). */}
        <dd className={overPlanned ? 'negative' : undefined}>{formatEuro(Math.abs(data.unplanned))}</dd>
        <dd className="tile-note">{overPlanned ? 'mehr verplant als Ø Einnahmen' : 'ohne Budget'}</dd>
      </div>
    </dl>
  )
}

/** The page owns its month: from when a target applies (decision 28). The current month is the default (US2). */
export function PlanningPage() {
  const [month, setMonth] = useState(currentMonth)
  return (
    <section>
      <PageHeader title="Planung">
        <MonthNav month={month} onChange={setMonth} label="Gilt ab" />
      </PageHeader>
      <PlanningContent month={month} />
    </section>
  )
}

/** The actual of a category in a month: its expenses, or its net when it was income (R2). */
function actualOf(data: DashboardData | undefined, id: number): { amount: number; income: boolean } {
  const expense = data?.categories.find((line) => line.id === id)
  if (expense && expense.actual > 0) {
    return { amount: expense.actual, income: false }
  }
  const income = data?.income.find((line) => line.id === id)
  if (income) {
    return { amount: income.actual, income: true }
  }
  return { amount: 0, income: false }
}

/** R24: hidden unless it has a target in the month "Gilt ab"; categories without entries are never income-only. */
function hiddenByDefault(category: Category): boolean {
  return category.incomeOnly && category.targetAmount === null && category.targetPercent === null
}

function PlanningContent({ month }: { month: string }) {
  const categories = useCategories(month)
  const dashboard = useDashboard(month)
  const previousMonth = shiftMonth(month, -1)
  const previous = useDashboard(previousMonth)
  const updateTarget = useUpdateTarget()
  const [showIncomeOnly, setShowIncomeOnly] = useState(false)

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const active = categories.data.filter((category) => !category.archived)
  const incomeOnly = active.filter(hiddenByDefault)
  const shown = showIncomeOnly ? active : active.filter((category) => !hiddenByDefault(category))

  return (
    <>
      <p className="hint plan-intro">
        Lege fest, wie viel du je Kategorie im Monat ausgeben willst, in Euro oder in Prozent deiner durchschnittlichen
        Einnahmen. Es gilt ab <strong>{formatMonth(month)}</strong> für alle folgenden Monate, bis ein späterer Monat
        ein neues Soll bekommt; frühere Monate bleiben unverändert. Gespeichert wird automatisch, sobald du ein Feld
        verlässt oder Enter drückst.
      </p>
      <PlanningBar data={dashboard.data} />

      <div className="card plan-list">
        <div className="plan-head" aria-hidden="true">
          <span>Kategorie</span>
          <span>Soll pro Monat</span>
          <span>Ist im {formatMonth(previousMonth)}</span>
        </div>
        {shown.map((category) => {
          const actual = actualOf(previous.data, category.id)
          return (
            <TargetRow
              key={`${category.id}-${month}`}
              category={category}
              month={month}
              targetEuro={dashboard.data?.categories.find((line) => line.id === category.id)?.target ?? null}
              previousActual={actual.amount}
              previousIsIncome={actual.income}
              previousMonth={previousMonth}
              onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, month, input })}
            />
          )
        })}
        {incomeOnly.length > 0 && (
          <div className="toggle-line">
            <span className="hint">
              {showIncomeOnly
                ? 'Kategorien, die bisher nur Einnahmen haben, werden angezeigt.'
                : `${incomeOnly.length === 1 ? '1 Kategorie' : `${incomeOnly.length} Kategorien`} mit ausschließlich Einnahmen ausgeblendet: ${incomeOnly.map((category) => category.name).join(', ')}`}
            </span>
            <button type="button" onClick={() => setShowIncomeOnly(!showIncomeOnly)}>
              {showIncomeOnly ? 'Ausblenden' : 'Einblenden'}
            </button>
          </div>
        )}
      </div>
    </>
  )
}
