import type { ImportPreview, ImportSeries } from './api/imports'
import type { BudgetSuggestion } from './api/types'

// Decision 91: what an import in the setup wizard suggests, kept in the browser session for the later steps; only
// display data of the preview, nothing is decided here

const KEY = 'setup-import'
// Review 23.11.2026: set while the setup wizard is open, so the import knows it also when the setup was done before
const OPEN_KEY = 'setup-open'

export interface SetupImport {
  series: ImportSeries[]
  budgetSuggestions: BudgetSuggestion[]
  income: { average: number | null; minimum: number | null; months: number }
  /** new rows of the files and the rows of their series, for the summary on the step Import */
  rows: number
  recurring: number
  /** the fingerprints counted, so a file read twice counts once */
  fingerprints: string[]
  recurringPrints: string[]
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
  // The new rows, and those the import preselects in stage 2: rows of a series or rows that fulfil a term
  const seriesLines = new Set(preview.series.flatMap((s) => s.lines))
  const fresh = preview.entries.filter((e) => e.status === 'NEW' && e.fingerprint !== null)
  const prints = new Set([...(known?.fingerprints ?? []), ...fresh.map((e) => e.fingerprint as string)])
  const recurringPrints = new Set([
    ...(known?.recurringPrints ?? []),
    ...fresh.filter((e) => seriesLines.has(e.line) || e.replaces !== null).map((e) => e.fingerprint as string),
  ])
  const next: SetupImport = {
    series,
    budgetSuggestions: budgets,
    income: preview.income.average !== null ? preview.income : (known?.income ?? preview.income),
    rows: prints.size,
    recurring: recurringPrints.size,
    fingerprints: [...prints],
    recurringPrints: [...recurringPrints],
  }
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // only a convenience; without storage the wizard shows the suggestions of the stored bookings
  }
}

/** The wizard is open; the import page then keeps the file's suggestions and leads back. */
export function markSetupOpen() {
  try {
    window.sessionStorage.setItem(OPEN_KEY, '1')
  } catch {
    // only a convenience
  }
}

export function isSetupOpen(): boolean {
  try {
    return window.sessionStorage.getItem(OPEN_KEY) === '1'
  } catch {
    return false
  }
}

/** Review 23.11.2026: the setup is finished or put off; its session data must not show up in a later setup. */
export function clearSetupSession(keys: string[]) {
  try {
    for (const key of [KEY, OPEN_KEY, ...keys]) {
      window.sessionStorage.removeItem(key)
    }
  } catch {
    // nothing to clear
  }
}
