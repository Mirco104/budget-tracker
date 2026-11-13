import { useState } from 'react'
import { useCategories, useCreateCategory } from '../api/categories'
import { useCreatePlannedItem, usePlan, useUpdatePlannedItem } from '../api/plan'
import type { Category, Plan, PlanGroup, PlannedItem } from '../api/types'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
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
  { group: 'SAVING', title: 'Sparen', hint: 'Sparraten und Rücklagen, die du beim Abhaken einträgst.' },
]

type DialogState = { item: PlannedItem | null; group?: PlanGroup; preset?: { name?: string; amount?: number; categoryId?: number } }

/** The page owns its month: the normal month shown and the default of "Gilt ab" (section 19, decision 49). */
export function PlanningPage() {
  const [month, setMonth] = useState(currentMonth)
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
  onToSaving,
  onToOther,
  busy,
}: {
  plan: Plan
  onToSaving: () => void
  onToOther: () => void
  busy: boolean
}) {
  const over = plan.unplanned < 0
  return (
    <div className="card plan-summary">
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
        <div className="plan-actions">
          <button type="button" className="saving-button" onClick={onToSaving} disabled={busy}>
            Zum Sparen
          </button>
          <button type="button" onClick={onToOther} disabled={busy}>
            Zu Sonstiges
          </button>
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

function GroupSection({
  title,
  hint,
  group,
  plan,
  onEdit,
  onAdd,
}: {
  title: string
  hint: string
  group: PlanGroup
  plan: Plan
  onEdit: (item: PlannedItem) => void
  onAdd: () => void
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
      {group === 'INCOME' && plan.averageIncome !== null && plan.minimumIncome !== null && (
        <p className="hint">
          Zur Orientierung: Ø {formatEuro(plan.averageIncome)}, mindestens {formatEuro(plan.minimumIncome)} in den
          letzten {plan.averageMonths === 1 ? 'Monat' : `${plan.averageMonths} Monaten`}.
        </p>
      )}
      {items.length === 0 && <p className="hint">Noch keine Posten.</p>}
      {byCategory ? (
        categories.map((line) => (
          <div key={line.categoryId} className="plan-category-block">
            <div className="plan-category-head">
              <span>{line.categoryName}</span>
              <span>{formatEuro(line.total)}</span>
            </div>
            <ul className="item-list">
              {items
                .filter((item) => item.categoryId === line.categoryId)
                .map((item) => (
                  <ItemRow key={item.id} item={item} onEdit={() => onEdit(item)} />
                ))}
            </ul>
          </div>
        ))
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
        busy={createItem.isPending || updateItem.isPending || createCategory.isPending}
        onToSaving={() =>
          setDialog({ item: null, group: 'SAVING', preset: { name: 'Sparen', amount: data.unplanned } })
        }
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
      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </>
  )
}
