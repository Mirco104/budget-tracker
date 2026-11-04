import { useCategories, useUpdateTarget } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import type { DashboardData } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { formatEuro, formatMonth } from '../format'

/** Ø income (R23), planned targets and the unplanned rest (R4) for the month, taken from the dashboard. */
function PlanningSummary({ month }: { month: string }) {
  const { data } = useDashboard(month)
  if (!data) {
    return null
  }
  if (data.averageIncome === null || data.unplanned === null) {
    return (
      <p className="hint">
        Noch kein abgeschlossener Monat mit Einträgen. Prozent-Soll zählen erst, wenn es einen gibt, denn sie
        beziehen sich auf die durchschnittlichen Einnahmen der Monate davor.
      </p>
    )
  }
  return (
    <dl className="summary">
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
        {/* The label already says "Überplant", so the amount is shown without minus sign (R4). */}
        <dd className={data.unplanned < 0 ? 'negative' : undefined}>{formatEuro(Math.abs(data.unplanned))}</dd>
      </div>
    </dl>
  )
}

/** "Januar 2026 – September 2026, 9 Monate" */
function averagePeriod(data: DashboardData): string {
  const months = data.averageIncomeMonths === 1 ? '1 Monat' : `${data.averageIncomeMonths} Monate`
  if (!data.averageIncomeFrom || !data.averageIncomeTo) {
    return months
  }
  const period =
    data.averageIncomeFrom === data.averageIncomeTo
      ? formatMonth(data.averageIncomeFrom)
      : `${formatMonth(data.averageIncomeFrom)} – ${formatMonth(data.averageIncomeTo)}`
  return `${period}, ${months}`
}

interface Props {
  month: string
  /** Shows the month navigation in the page header. */
  onMonthChange?: (month: string) => void
}

export function PlanningPage({ month, onMonthChange }: Props) {
  return (
    <section>
      <PageHeader title="Planung">{onMonthChange && <MonthNav month={month} onChange={onMonthChange} />}</PageHeader>
      <PlanningContent month={month} />
    </section>
  )
}

function PlanningContent({ month }: { month: string }) {
  const categories = useCategories(month)
  const updateTarget = useUpdateTarget()

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  return (
    <>
      <p className="hint">
        Soll je Kategorie in Euro oder in Prozent der durchschnittlichen Einnahmen der letzten 12 Monate. Ist beides gesetzt, gilt der Euro-Wert. Änderungen
        werden automatisch gespeichert, sobald du ein Feld verlässt oder Enter drückst.
      </p>
      <p className="hint">
        Gilt ab <strong>{formatMonth(month)}</strong> für alle folgenden Monate, bis ein späterer Monat ein neues Soll
        bekommt. Frühere Monate bleiben unverändert.
      </p>
      <PlanningSummary month={month} />

      <h3>Kategorien</h3>
      {categories.data.filter((category) => !category.archived).map((category) => (
        <TargetRow
          key={`${category.id}-${month}`}
          category={category}
          month={month}
          onSubmit={(input) => updateTarget.mutateAsync({ id: category.id, month, input })}
        />
      ))}
    </>
  )
}
