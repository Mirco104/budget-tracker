import type { ImportPreview, ImportSeries } from './api/imports'
import type { BudgetSuggestion } from './api/types'

// Decision 91: what an import in the setup wizard suggests, kept in the browser session for the later steps; only
// display data of the preview, nothing is decided here

const KEY = 'setup-import'

export interface SetupImport {
  series: ImportSeries[]
  budgetSuggestions: BudgetSuggestion[]
  income: { average: number | null; minimum: number | null; months: number }
  /** new rows of the files and the rows of their series, for the summary on the step Import */
  rows: number
  recurring: number
}

export function readSetupImport(): SetupImport | null {
  try {
    return JSON.parse(window.sessionStorage.getItem(KEY) ?? 'null') as SetupImport | null
  } catch {
    return null
  }
}

/** Adds the suggestions of a preview to those of the files read before in this session. */
export function addSetupImport(preview: ImportPreview) {
  const known = readSetupImport()
  const seriesKey = (s: ImportSeries) => `${s.source}|${s.description}|${s.amount}`
  const budgetKey = (b: BudgetSuggestion) => `${b.categoryId ?? ''}|${b.bankCategory ?? ''}`
  const series = [...(known?.series ?? []).filter((s) => !preview.series.some((n) => seriesKey(n) === seriesKey(s))), ...preview.series]
  const budgets = [
    ...(known?.budgetSuggestions ?? []).filter((b) => !preview.budgetSuggestions.some((n) => budgetKey(n) === budgetKey(b))),
    ...preview.budgetSuggestions,
  ]
  const next: SetupImport = {
    series,
    budgetSuggestions: budgets,
    income: preview.income.average !== null ? preview.income : (known?.income ?? preview.income),
    rows: (known?.rows ?? 0) + preview.newRows,
    recurring: (known?.recurring ?? 0) + preview.series.reduce((sum, s) => sum + s.lines.length, 0),
  }
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // only a convenience; without storage the wizard shows the suggestions of the stored bookings
  }
}

export function clearSetupImport() {
  try {
    window.sessionStorage.removeItem(KEY)
  } catch {
    // nothing to clear
  }
}
