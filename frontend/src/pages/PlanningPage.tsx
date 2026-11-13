import { useState } from 'react'
import { useCategories, useCreateCategory } from '../api/categories'
import { useCreatePlannedItem, usePlan, useUpdatePlannedItem } from '../api/plan'
import type { Category, Plan, PlanGroup, PlannedItem } from '../api/types'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
import { SplitSavingDialog } from '../components/SplitSavingDialog'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { InfoTip } from '../components/ui/InfoTip'
import { Loading } from '../components/ui/Loading'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { Toast } from '../components/ui/Toast'
import { currentMonth, formatEuro, formatMonth } from '../format'
import { rhythmLabel } from '../labels'

/** The standard catch-all budget (concept 3.7); created on first use. */
const SONSTIGES = 'Sonstiges'

const GROUPS: { group: PlanGroup; title: string; hint: string }[] = [
  { group: 'INCOME', title: 'Einkommen', hint: 'Was regelmäßig hereinkommt, z. B. Gehalt.' },
  { group: 'FIXED', title: 'Fixkosten', hint: 'Ausgaben mit festem Termin, z. B. Miete, Versicherungen, Abos.' },
  {
    group: 'BUDGET',
    title: 'Budgets (variable Kosten)',
    hint: 'Was du im Monat ausgeben willst, ohne festen Termin, z. B. Lebensmittel, Benzin.',
  },
  {
    group: 'SAVING',
    title: 'Sparen',
    hint: 'Was du regelmäßig zurücklegst, z. B. Notgroschen oder ETF-Sparplan.',
  },
]

type DialogState = { item: PlannedItem | null; group?: PlanGroup; preset?: { name?: string; amount?: number; categoryId?: number } }

/**
 * The normal month shown and the default of "Gilt ab" (section 19, decision 49). The month is shared with the
 * overview (27f2); without props the page keeps its own, starting at the current month.
 */
export function PlanningPage({ month: shared, onMonthChange }: { month?: string; onMonthChange?: (month: string) => void }) {
  const [own, setOwn] = useState(currentMonth)
  const month = shared ?? own
  const setMonth = onMonthChange ?? setOwn
  return (
    <section>
      <PageHeader title="Planung">
        <MonthNav month={month} onChange={setMonth} />
      </PageHeader>
      <PlanningContent month={month} />
    </section>
  )
}

/** "1.200,00 € jährlich" for an item that is not a monthly amount, otherwise nothing. */
function paymentNote(item: PlannedItem): string | null {
  if (item.rhythmUnit === 'MONTH' && item.rhythmCount === 1) {
    return 'monatlich'
  }
  if (item.rhythmUnit === null) {
    return null
  }
  return `${formatEuro(Math.abs(item.amount))} ${rhythmLabel(item.rhythmUnit, item.rhythmCount)}`
}

function ItemRow({ item, onEdit }: { item: PlannedItem; onEdit: () => void }) {
  const note = paymentNote(item)
  return (
    <li>
      <button type="button" className="item-row" onClick={onEdit}>
        <span className="item-main">
          <span className="item-name">{item.name}</span>
          <span className="tx-meta">
            {note}
            {item.entryMode === 'AUTO' && <span className="badge auto">automatisch</span>}
            {item.entryMode === 'TICK' && <span className="badge">abhaken</span>}
          </span>
        </span>
        <span className="item-amount">{formatEuro(item.monthlyShare)}</span>
      </button>
    </li>
  )
}

/** P4: a bar of fixed costs, budgets and saving against the planned income, the rest grey. */
function PlanBar({ plan }: { plan: Plan }) {
  const base = Math.max(plan.income, plan.fixed + plan.budgets + plan.saving, 0.01)
  const part = (value: number) => `${Math.max(0, (value / base) * 100)}%`
  return (
    <div className="plan-stack" aria-hidden="true">
      <span className="stack-fixed" style={{ width: part(plan.fixed) }} />
      <span className="stack-budget" style={{ width: part(plan.budgets) }} />
      <span className="stack-saving" style={{ width: part(plan.saving) }} />
      <span className="stack-rest" style={{ width: part(Math.max(0, plan.unplanned)) }} />
    </div>
  )
}

function PlanHead({
  plan,
  other,
  onToSaving,
  onToOther,
  busy,
}: {
  plan: Plan
  /** 27f3: the budget of the category "Sonstiges", undefined without one. */
  other?: number
  onToSaving: () => void
  onToOther: () => void
  busy: boolean
}) {
  const over = plan.unplanned < 0
  return (
    <div className="card plan-summary">
      {/* 27f4: the planned income the bar divides */}
      <p className="plan-income">
        <span>Geplante Einnahmen</span>
        <strong className="income">{formatEuro(plan.income)}</strong>
      </p>
      <PlanBar plan={plan} />
      <dl className="plan-legend">
        <div>
          <dt>
            <span className="dot stack-fixed" /> Fixkosten
          </dt>
          <dd>{formatEuro(plan.fixed)}</dd>
        </div>
        <div>
          <dt>
            <span className="dot stack-budget" /> Budgets
          </dt>
          <dd>{formatEuro(plan.budgets)}</dd>
          {/* 27f3: the catch-all is shown, so its size does not hide */}
          {other !== undefined && <dd className="tile-note">davon Sonstiges {formatEuro(other)}</dd>}
        </div>
        <div>
          <dt>
            <span className="dot stack-saving" /> Sparen
          </dt>
          <dd>
            {formatEuro(plan.saving)}
            {plan.savingRate !== null && <span className="tile-note"> · {plan.savingRate.toLocaleString('de-DE')} %</span>}
          </dd>
        </div>
        <div className={over ? 'over' : undefined}>
          <dt>
            <span className="dot stack-rest" /> {over ? 'Überplant' : 'Nicht verplant'}
            <InfoTip label="Was heißt nicht verplant?">
              Nicht verplant = Einkommen − Fixkosten − Budgets − Sparen, alles als Monatsanteil.
            </InfoTip>
          </dt>
          <dd>{formatEuro(Math.abs(plan.unplanned))}</dd>
        </div>
      </dl>
      {plan.unplanned > 0 ? (
        <div className="unplanned-question">
          <p>
            <strong>{formatEuro(plan.unplanned)} sind noch nicht verplant.</strong> Was soll damit passieren?
          </p>
          <div className="plan-actions">
            <button type="button" className="saving-button" onClick={onToSaving} disabled={busy}>
              Monatlich sparen
            </button>
            <button type="button" onClick={onToOther} disabled={busy}>
              Als Puffer in „Sonstiges“
            </button>
          </div>
        </div>
      ) : (
        plan.unplanned === 0 &&
        plan.income > 0 && (
          <p className="plan-done">
            ✓ Alles verplant
          </p>
        )
      )}
    </div>
  )
}

/** 27f2: a category of fixed costs or budgets like a budget row of the overview; open it shows its items. */
function CategoryRow({
  name,
  icon,
  total,
  items,
  onEdit,
}: {
  name: string
  icon: string
  total: number
  items: PlannedItem[]
  onEdit: (item: PlannedItem) => void
}) {
  const [open, setOpen] = useState(false)
  return (
    <li className="budget-item">
      <button type="button" className="budget-head budget-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <CategoryIcon icon={icon} />
        <span className="budget-name">{name}</span>
        <span className="budget-amount">
          <span>{formatEuro(total)}</span>
          <span className="budget-left">{items.length === 1 ? '1 Posten' : `${items.length} Posten`}</span>
        </span>
      </button>
      {open && (
        <ul className="item-list">
          {items.map((item) => (
            <ItemRow key={item.id} item={item} onEdit={() => onEdit(item)} />
          ))}
        </ul>
      )}
    </li>
  )
}

function GroupSection({
  title,
  hint,
  group,
  plan,
  onEdit,
  onAdd,
  iconOf,
}: {
  title: string
  hint: string
  group: PlanGroup
  plan: Plan
  onEdit: (item: PlannedItem) => void
  onAdd: () => void
  iconOf: (categoryId: number) => string
}) {
  const items = plan.items.filter((item) => item.group === group)
  const total = { INCOME: plan.income, FIXED: plan.fixed, BUDGET: plan.budgets, SAVING: plan.saving }[group]
  // Fixed costs and budgets are grouped by category with a subtotal (concept 4.3)
  const byCategory = group === 'FIXED' || group === 'BUDGET'
  const categories = plan.categories.filter((line) => line.group === group)
  return (
    <section className="card plan-group" aria-label={title}>
      <div className="plan-group-head">
        <h2>
          {title} <InfoTip label={`Was gehört zu ${title}?`}>{hint}</InfoTip>
        </h2>
        <span className="plan-group-total">{formatEuro(total)}</span>
      </div>
      {/* 27f4: average and weakest month of the past months as two tiles, for orientation (R23) */}
      {group === 'INCOME' && plan.averageIncome !== null && plan.minimumIncome !== null && (
        <div className="income-stats">
          <div className="income-stat">
            <span className="income-stat-label">Durchschnitt</span>
            <span className="income-stat-value">{formatEuro(plan.averageIncome)}</span>
            <span className="income-stat-note">pro Monat</span>
          </div>
          <div className="income-stat">
            <span className="income-stat-label">Mindestens</span>
            <span className="income-stat-value">{formatEuro(plan.minimumIncome)}</span>
            <span className="income-stat-note">im schwächsten Monat</span>
          </div>
          <p className="hint income-stats-period">
            {plan.averageFrom && plan.averageTo && plan.averageFrom !== plan.averageTo
              ? `${formatMonth(plan.averageFrom)} – ${formatMonth(plan.averageTo)} · `
              : plan.averageFrom
                ? `${formatMonth(plan.averageFrom)} · `
                : ''}
            {plan.averageMonths === 1 ? '1 Monat' : `${plan.averageMonths} Monate`}
          </p>
          {plan.incomeAboveMinimum && (
            <p className="hint income-stats-warn">Geplant ist mehr als deine Mindesteinnahmen.</p>
          )}
        </div>
      )}
      {items.length === 0 && <p className="hint">Noch keine Posten.</p>}
      {byCategory ? (
        <ul className="budget-list">
          {categories.map((line) => (
            <CategoryRow
              key={line.categoryId}
              name={line.categoryName}
              icon={iconOf(line.categoryId)}
              total={line.total}
              items={items.filter((item) => item.categoryId === line.categoryId)}
              onEdit={onEdit}
            />
          ))}
        </ul>
      ) : (
        <ul className="item-list">
          {items.map((item) => (
            <ItemRow key={item.id} item={item} onEdit={() => onEdit(item)} />
          ))}
        </ul>
      )}
      <button type="button" className="link-button" onClick={onAdd}>
        + Posten
      </button>
    </section>
  )
}

function PlanningContent({ month }: { month: string }) {
  const plan = usePlan(month)
  const categories = useCategories(month)
  const createCategory = useCreateCategory()
  const createItem = useCreatePlannedItem()
  const updateItem = useUpdatePlannedItem()
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [splitting, setSplitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (plan.isPending || categories.isPending) {
    return <Loading text="Lade Planung …" />
  }
  if (plan.error || categories.error) {
    return <p className="error">Planung konnte nicht geladen werden: {(plan.error ?? categories.error)?.message}</p>
  }
  const data = plan.data
  const noBudgets = !data.items.some((item) => item.group === 'BUDGET')

  // P4 "Zu Sonstiges": the rest goes to the budget of the catch-all category, from this month on
  async function toOther() {
    setError(null)
    try {
      let category: Category | undefined = categories.data?.find((c) => c.name === SONSTIGES && !c.archived)
      if (!category) {
        category = await createCategory.mutateAsync({ name: SONSTIGES })
      }
      const existing = data.items.find((item) => item.group === 'BUDGET' && item.categoryId === category.id)
      if (existing) {
        await updateItem.mutateAsync({
          id: existing.id,
          input: {
            name: existing.name,
            amount: -(existing.monthlyShare + data.unplanned),
            categoryId: existing.categoryId,
            accountId: existing.accountId,
            rhythmUnit: null,
            rhythmCount: null,
            anchorDate: null,
            entryMode: existing.entryMode,
            validFrom: month,
          },
        })
      } else {
        await createItem.mutateAsync({
          name: SONSTIGES,
          amount: -data.unplanned,
          categoryId: category.id,
          rhythmUnit: null,
          rhythmCount: null,
          anchorDate: null,
          validFrom: month,
        })
      }
      setToast(`${formatEuro(data.unplanned)} zu Sonstiges ab ${formatMonth(month)}.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Speichern fehlgeschlagen.')
    }
  }

  return (
    <>
      <p className="hint plan-intro">
        Plane einen <strong>normalen Monat</strong>: Trage ein, was regelmäßig kommt und geht. Seltene Zahlungen
        (z. B. 1.200 € jährlich) zählen mit ihrem Monatsanteil. Änderungen gelten ab einem Monat; frühere Monate behalten
        ihren Plan.
      </p>
      <PlanHead
        plan={data}
        other={data.categories.find((line) => line.group === 'BUDGET' && line.categoryName === SONSTIGES)?.total}
        busy={createItem.isPending || updateItem.isPending || createCategory.isPending}
        onToSaving={() => setSplitting(true)}
        onToOther={() => void toOther()}
      />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {noBudgets && (
        <p className="hint plan-onboarding">
          Lege deine Budgets für variable Kosten an, z. B. Lebensmittel oder Freizeit.
        </p>
      )}
      {GROUPS.map(({ group, title, hint }) => (
        <GroupSection
          key={group}
          title={title}
          hint={hint}
          group={group}
          plan={data}
          onEdit={(item) => setDialog({ item })}
          onAdd={() => setDialog({ item: null, group })}
          iconOf={(id) => categories.data?.find((c) => c.id === id)?.icon ?? 'tag'}
        />
      ))}
      {dialog && (
        <PlannedItemDialog
          categories={categories.data}
          item={dialog.item}
          month={month}
          group={dialog.group}
          preset={dialog.preset}
          onClose={() => setDialog(null)}
          onDone={(message) => {
            setDialog(null)
            setToast(message)
          }}
        />
      )}
      {splitting && (
        <SplitSavingDialog
          amount={data.unplanned}
          items={data.items.filter((item) => item.group === 'SAVING')}
          month={month}
          onNewItem={() => {
            setSplitting(false)
            setDialog({ item: null, group: 'SAVING', preset: { name: 'Sparen', amount: data.unplanned } })
          }}
          onClose={() => setSplitting(false)}
          onDone={(message) => {
            setSplitting(false)
            setToast(message)
          }}
        />
      )}
      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </>
  )
}
