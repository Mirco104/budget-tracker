import { ResponsiveContainer, Sankey, Tooltip, type SankeyNodeProps } from 'recharts'
import type { Plan, PlanGroup } from '../api/types'
import { formatEuro } from '../format'

type Kind = 'income' | 'fixed' | 'budget' | 'saving' | 'rest'

const COLOR: Record<Kind, string> = {
  income: 'var(--income)',
  fixed: 'var(--plan-fixed)',
  budget: 'var(--plan-budget)',
  saving: 'var(--saving)',
  rest: 'var(--plan-rest)',
}

// A node with its name and amount next to it; the income on the left, all others on the right of their bar
function Node({ x, y, width, height, payload: raw }: SankeyNodeProps) {
  const payload = raw as unknown as { name: string; kind: Kind; value: number }
  const left = payload.kind === 'income'
  return (
    <g>
      <rect x={x} y={y} width={width} height={Math.max(height, 1.5)} fill={COLOR[payload.kind]} rx={2} />
      <text
        x={left ? x - 6 : x + width + 6}
        y={y + Math.max(height, 1.5) / 2}
        textAnchor={left ? 'end' : 'start'}
        dominantBaseline="middle"
        className="sankey-label"
      >
        {payload.name} <tspan className="sankey-value">{formatEuro(payload.value)}</tspan>
      </text>
    </g>
  )
}

/**
 * US40: the plan of a month as a Sankey diagram, income → fixed costs, budgets, saving and not planned → categories
 * (saving items by name). Only the sums of the plan (P4) are shown; nothing is calculated here.
 */
export function PlanSankey({ plan }: { plan: Plan }) {
  const nodes: { name: string; kind: Kind }[] = [{ name: 'Einkommen', kind: 'income' }]
  const links: { source: number; target: number; value: number }[] = []
  const groups: { group: PlanGroup; name: string; kind: Kind; value: number }[] = [
    { group: 'FIXED', name: 'Fixkosten', kind: 'fixed', value: plan.fixed },
    { group: 'BUDGET', name: 'Variable Kosten', kind: 'budget', value: plan.budgets },
    { group: 'SAVING', name: 'Sparen', kind: 'saving', value: plan.saving },
  ]
  for (const g of groups) {
    if (g.value <= 0) {
      continue
    }
    const at = nodes.length
    nodes.push({ name: g.name, kind: g.kind })
    links.push({ source: 0, target: at, value: g.value })
    const children =
      g.group === 'SAVING'
        ? plan.items.filter((i) => i.group === 'SAVING').map((i) => ({ name: i.name, value: i.monthlyShare }))
        : plan.categories.filter((c) => c.group === g.group).map((c) => ({ name: c.categoryName, value: c.total }))
    for (const child of children.filter((c) => c.value > 0).sort((a, b) => b.value - a.value)) {
      nodes.push({ name: child.name, kind: g.kind })
      links.push({ source: at, target: nodes.length - 1, value: child.value })
    }
  }
  if (plan.unplanned > 0) {
    nodes.push({ name: 'Nicht verplant', kind: 'rest' })
    links.push({ source: 0, target: nodes.length - 1, value: plan.unplanned })
  }
  if (links.length === 0) {
    return <p className="hint">Noch nichts geplant.</p>
  }
  const height = Math.max(260, nodes.length * 26)
  return (
    <div className="plan-sankey" role="img" aria-label="Wohin dein Einkommen im Plan fließt">
      <ResponsiveContainer width="100%" height={height}>
        <Sankey
          data={{ nodes, links }}
          node={(props: SankeyNodeProps) => <Node {...props} />}
          nodePadding={10}
          nodeWidth={12}
          margin={{ top: 8, right: 190, bottom: 8, left: 140 }}
          link={{ stroke: 'var(--line-strong)', strokeOpacity: 0.35 }}
        >
          <Tooltip formatter={(value) => formatEuro(Number(value))} />
        </Sankey>
      </ResponsiveContainer>
    </div>
  )
}
