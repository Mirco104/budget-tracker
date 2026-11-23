import { useEffect, useState } from 'react'
import { ResponsiveContainer, Sankey, Tooltip, type SankeyLinkProps, type SankeyNodeProps } from 'recharts'
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

// A link as a band in the color of its target, so each flow can be followed to its group
function Link({ sourceX, targetX, sourceY, targetY, sourceControlX, targetControlX, linkWidth, payload }: SankeyLinkProps) {
  const target = payload.target as unknown as { kind: Kind }
  return (
    <path
      d={`M${sourceX},${sourceY} C${sourceControlX},${sourceY} ${targetControlX},${targetY} ${targetX},${targetY}`}
      fill="none"
      stroke={COLOR[target.kind]}
      strokeOpacity={0.3}
      strokeWidth={Math.max(linkWidth, 1)}
    />
  )
}

interface Flow {
  name: string
  kind: Kind
  value: number
  children: { name: string; value: number }[]
}

/** The groups of the plan with their categories (saving items by name), in the order on screen. */
function flowsOf(plan: Plan): Flow[] {
  const groups: { group: PlanGroup; name: string; kind: Kind; value: number }[] = [
    { group: 'FIXED', name: 'Fixkosten', kind: 'fixed', value: plan.fixed },
    { group: 'BUDGET', name: 'Variable Kosten', kind: 'budget', value: plan.budgets },
    { group: 'SAVING', name: 'Sparen', kind: 'saving', value: plan.saving },
  ]
  const flows: Flow[] = groups
    .filter((g) => g.value > 0)
    .map((g) => ({
      name: g.name,
      kind: g.kind,
      value: g.value,
      children: (g.group === 'SAVING'
        ? plan.items.filter((i) => i.group === 'SAVING').map((i) => ({ name: i.name, value: i.monthlyShare }))
        : plan.categories.filter((c) => c.group === g.group).map((c) => ({ name: c.categoryName, value: c.total }))
      )
        .filter((c) => c.value > 0)
        .sort((a, b) => b.value - a.value),
    }))
  if (plan.unplanned > 0) {
    flows.push({ name: 'Nicht verplant', kind: 'rest', value: plan.unplanned, children: [] })
  }
  return flows
}

const NARROW = '(max-width: 600px)'

// Field test after 0.3.0: on a phone held upright the Sankey has no room for its labels
function useNarrow(): boolean {
  const [narrow, setNarrow] = useState(() => mediaQuery()?.matches ?? false)
  useEffect(() => {
    const query = mediaQuery()
    if (query === null) {
      return
    }
    const change = (event: MediaQueryListEvent) => setNarrow(event.matches)
    query.addEventListener('change', change)
    return () => query.removeEventListener('change', change)
  }, [])
  return narrow
}

function mediaQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(NARROW) : null
}

/**
 * Field test after 0.3.0: the same flows as a list for narrow screens, each group with a bar of its share of the income
 * and its categories below. Only the sums of the plan are shown; the width of a bar is display only.
 */
function FlowList({ plan, flows }: { plan: Plan; flows: Flow[] }) {
  const base = Math.max(plan.income, flows.reduce((sum, f) => sum + f.value, 0), 0.01)
  return (
    <div className="plan-flow" role="list" aria-label="Wohin dein Einkommen im Plan fließt">
      <div className="plan-flow-income">
        <span>Einkommen</span>
        <strong>{formatEuro(plan.income)}</strong>
      </div>
      {flows.map((flow) => (
        <div key={flow.name} className="plan-flow-group" role="listitem">
          <div className="plan-flow-head">
            <span>{flow.name}</span>
            <strong>{formatEuro(flow.value)}</strong>
          </div>
          <div className="plan-flow-bar" aria-hidden="true">
            <span style={{ width: `${(flow.value / base) * 100}%`, background: COLOR[flow.kind] }} />
          </div>
          {flow.children.length > 0 && (
            <ul className="plan-flow-children">
              {flow.children.map((child, index) => (
                <li key={`${index}:${child.name}`}>
                  <span>{child.name}</span>
                  <span>{formatEuro(child.value)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  )
}

/**
 * US40: the plan of a month as a Sankey diagram, income → fixed costs, budgets, saving and not planned → categories
 * (saving items by name). Only the sums of the plan (P4) are shown; nothing is calculated here. On a narrow screen
 * the same flows show as a list (field test 24.11.2026).
 */
export function PlanSankey({ plan }: { plan: Plan }) {
  const narrow = useNarrow()
  const flows = flowsOf(plan)
  if (flows.length === 0) {
    return <p className="hint">Noch nichts geplant.</p>
  }
  if (narrow) {
    return <FlowList plan={plan} flows={flows} />
  }
  // The order of the nodes is the order on screen (sort off): groups top down, their categories in the same order
  const nodes: { name: string; kind: Kind }[] = [{ name: 'Einkommen', kind: 'income' }]
  const links: { source: number; target: number; value: number }[] = []
  for (const flow of flows) {
    const at = nodes.length
    nodes.push({ name: flow.name, kind: flow.kind })
    links.push({ source: 0, target: at, value: flow.value })
    for (const child of flow.children) {
      nodes.push({ name: child.name, kind: flow.kind })
      links.push({ source: at, target: nodes.length - 1, value: child.value })
    }
  }
  const height = Math.max(260, nodes.length * 26)
  return (
    <div className="plan-sankey" role="img" aria-label="Wohin dein Einkommen im Plan fließt">
      <ResponsiveContainer width="100%" height={height}>
        <Sankey
          data={{ nodes, links }}
          node={(props: SankeyNodeProps) => <Node {...props} />}
          link={(props: SankeyLinkProps) => <Link {...props} />}
          nodePadding={12}
          nodeWidth={12}
          // Fixed order without crossings; "Nicht verplant" stays next to the groups instead of moving to the right edge
          sort={false}
          iterations={0}
          align="left"
          margin={{ top: 8, right: 190, bottom: 8, left: 140 }}
        >
          <Tooltip formatter={(value) => formatEuro(Number(value))} />
        </Sankey>
      </ResponsiveContainer>
    </div>
  )
}
