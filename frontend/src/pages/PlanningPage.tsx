import { useState } from 'react'
import { useCategories, useUpdateTarget } from '../api/categories'
import { useDashboard } from '../api/dashboard'
import type { Category, DashboardData } from '../api/types'
import { TargetRow } from '../components/TargetRow'
import { MonthNav } from '../components/ui/MonthNav'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
import { currentMonth, formatEuro, formatMonth, formatMonthName, shiftMonth } from '../format'
import { averagePeriod } from '../labels'

export type PlanningBase = 'avg' | 'min'

const BASE_NAMES: Record<PlanningBase, string> = { avg: 'Ø Einnahmen', min: 'Mindesteinnahmen' }

// The chosen base is a convenience per browser (US7a); without storage the default applies
const BASE_KEY = 'planning-base'

function storedBase(): PlanningBase {
  try {
    return localStorage.getItem(BASE_KEY) === 'min' ? 'min' : 'avg'
  } catch {
    return 'avg'
  }
}

function storeBase(base: PlanningBase) {
  try {
    localStorage.setItem(BASE_KEY, base)
  } catch {
    // Storage blocked: the choice lasts until the page is left
  }
}

const percentFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 })

/** One of the two income tiles; clicking it makes it the base (US7a, decision 36). */
function BaseTile({
  label,
  value,
  selected,
  onSelect,
  period,
}: {
  label: string
  value: number
  selected: boolean
  onSelect: () => void
  period: string
}) {
  return (
    <button
      type="button"
      className={selected ? 'plan-tile base-tile selected' : 'plan-tile base-tile'}
      aria-pressed={selected}
      onClick={onSelect}
    >
      <span className="tile-head">
        <span className="tile-label">{label}</span>
        <span className="base-mark">{selected ? 'Basis' : 'als Basis wählen'}</span>
      </span>
      <span className="tile-value">{formatEuro(value)}</span>
      <span className="tile-note period">{period}</span>
    </button>
  )
}

/**
 * Bar that stays at the top while scrolling (US7a): Ø and minimum income (R23), planned targets and the unplanned
 * rest (R4) for the month "Gilt ab", taken from the dashboard. A click on an income tile makes it the base for
 * "Nicht verplant" and the input in percent. It refreshes after every saved target.
 */
function PlanningBar({
  data,
  base,
  onBaseChange,
}: {
  data: DashboardData | undefined
  base: PlanningBase
  onBaseChange: (base: PlanningBase) => void
}) {
  if (!data) {
    return null
  }
  if (data.averageIncome === null || data.minimumIncome === null || data.unplanned === null) {
    return (
      <div className="plan-bar">
        <p className="hint">
          Noch kein abgeschlossener Monat mit Einträgen. Ø Einnahmen, Mindesteinnahmen und „Nicht verplant“ gibt es,
          sobald einer da ist; bis dahin lässt sich das Soll nur in Euro eingeben.
        </p>
      </div>
    )
  }
  const useMinimum = base === 'min'
  const baseValue = useMinimum ? data.minimumIncome : data.averageIncome
  const unplanned = (useMinimum ? data.unplannedMinimum : data.unplanned) ?? 0
  const unplannedLabel = useMinimum ? data.unplannedMinimumLabel : data.unplannedLabel
  const overPlanned = unplanned < 0
  const period = averagePeriod(data)
  return (
    <div className="plan-bar plan-tiles">
      <BaseTile
        label="Ø Einnahmen"
        value={data.averageIncome}
        selected={!useMinimum}
        onSelect={() => onBaseChange('avg')}
        period={period}
      />
      <BaseTile
        label="Mindesteinnahmen"
        value={data.minimumIncome}
        selected={useMinimum}
        onSelect={() => onBaseChange('min')}
        period={period}
      />
      <div className="plan-tile">
        <span className="tile-label">Verplant</span>
        <span className="tile-value">{formatEuro(data.expenses.target)}</span>
        <span className="tile-note">
          {baseValue > 0 ? `${percentFormat.format((data.expenses.target / baseValue) * 100)} % der ${BASE_NAMES[base]}` : ''}
        </span>
      </div>
      <div className={overPlanned ? 'plan-tile over' : 'plan-tile'}>
        <span className="tile-label">{unplannedLabel}</span>
        {/* The label already says "Überplant", so the amount is shown without minus sign in the warning color (R4). */}
        <span className="tile-value">{formatEuro(Math.abs(unplanned))}</span>
        <span className="tile-note">{overPlanned ? `mehr verplant als ${BASE_NAMES[base]}` : 'ohne Budget'}</span>
      </div>
    </div>
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
  return category.incomeOnly && category.targetAmount === null
}

function PlanningContent({ month }: { month: string }) {
  const categories = useCategories(month)
  const dashboard = useDashboard(month)
  const previousMonth = shiftMonth(month, -1)
  const previous = useDashboard(previousMonth)
  const updateTarget = useUpdateTarget()
  const [showIncomeOnly, setShowIncomeOnly] = useState(false)
  const [base, setBase] = useState(storedBase)

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const baseValue = (base === 'min' ? dashboard.data?.minimumIncome : dashboard.data?.averageIncome) ?? null
  const active = categories.data.filter((category) => !category.archived)
  const incomeOnly = active.filter(hiddenByDefault)
  const shown = showIncomeOnly ? active : active.filter((category) => !hiddenByDefault(category))

  return (
    <>
      <p className="hint plan-intro">
        Lege fest, wie viel du je Kategorie im Monat ausgeben willst. Du kannst Euro oder Prozent der gewählten Basis
        eingeben, gespeichert wird immer der Euro-Betrag. Er gilt ab <strong>{formatMonth(month)}</strong> für alle
        folgenden Monate, bis ein späterer Monat ein neues Soll bekommt; frühere Monate bleiben unverändert.
        {dashboard.data?.averageIncomeFrom && <> Ø und Mindesteinnahmen stammen aus {averagePeriod(dashboard.data)}.</>}{' '}
        Gespeichert wird automatisch, sobald du ein Feld verlässt oder Enter drückst.
      </p>
      <PlanningBar
        data={dashboard.data}
        base={base}
        onBaseChange={(next) => {
          setBase(next)
          storeBase(next)
        }}
      />

      <div className="card plan-list">
        <div className="plan-head" aria-hidden="true">
          <span>Kategorie</span>
          <span>Soll pro Monat</span>
          <span className="plan-head-previous">
            <Icon name="history" size={14} />
            Ist im Vormonat ({formatMonthName(previousMonth)})
          </span>
        </div>
        {shown.map((category) => {
          const actual = actualOf(previous.data, category.id)
          return (
            <TargetRow
              key={`${category.id}-${month}`}
              category={category}
              month={month}
              base={baseValue}
              baseName={BASE_NAMES[base]}
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
