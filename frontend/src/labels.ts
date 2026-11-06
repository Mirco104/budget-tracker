import type { DashboardData, RecurringInterval } from './api/types'
import { formatMonth } from './format'

export const INTERVAL_LABELS: Record<RecurringInterval, string> = {
  MONTHLY: 'monatlich',
  QUARTERLY: 'vierteljährlich',
  HALF_YEARLY: 'halbjährlich',
  YEARLY: 'jährlich',
}

/** The same labels by the interval in months, as the fixed costs carry it (R25). */
export const INTERVAL_LABELS_BY_MONTHS: Record<number, string> = {
  1: 'monatlich',
  3: 'vierteljährlich',
  6: 'halbjährlich',
  12: 'jährlich',
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
