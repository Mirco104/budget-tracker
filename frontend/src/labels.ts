import type { DashboardData, RhythmUnit } from './api/types'
import { formatMonth } from './format'

/** The presets of "Wie oft" (concept 3.1): unit and count. */
export const RHYTHM_PRESETS: { unit: RhythmUnit; count: number; label: string }[] = [
  { unit: 'WEEK', count: 1, label: 'wöchentlich' },
  { unit: 'WEEK', count: 2, label: 'alle 2 Wochen' },
  { unit: 'MONTH', count: 1, label: 'monatlich' },
  { unit: 'MONTH', count: 2, label: 'alle 2 Monate' },
  { unit: 'MONTH', count: 3, label: 'vierteljährlich' },
  { unit: 'MONTH', count: 6, label: 'halbjährlich' },
  { unit: 'MONTH', count: 12, label: 'jährlich' },
]

/** "monatlich", "alle 4 Wochen" or "ohne festen Termin" for an item without a unit. */
export function rhythmLabel(unit: RhythmUnit | null, count: number | null): string {
  if (unit === null || count === null) {
    return 'ohne festen Termin'
  }
  const preset = RHYTHM_PRESETS.find((p) => p.unit === unit && p.count === count)
  return preset ? preset.label : `alle ${count} ${unit === 'WEEK' ? 'Wochen' : 'Monate'}`
}

/** "Januar 2026 – September 2026, 9 Monate": the months behind the Ø income (R23). */
export function averagePeriod(data: DashboardData): string {
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
