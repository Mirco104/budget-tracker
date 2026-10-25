import type { CategoryGroup, RecurringInterval } from './api/types'

export const GROUP_LABELS: Record<CategoryGroup, string> = {
  FIXED: 'Fixkosten',
  FLEXIBLE: 'Flexible Ausgaben',
  SAVINGS: 'Sparen & Investieren',
  REMAINDER: 'Rest',
}

/** Recommended share of the income per group (R10), display only. */
export const GROUP_RECOMMENDATIONS: Record<CategoryGroup, string | null> = {
  FIXED: 'ca. 50 %',
  FLEXIBLE: 'ca. 30 %',
  SAVINGS: 'ca. 20 %',
  REMAINDER: null,
}

export const INTERVAL_LABELS: Record<RecurringInterval, string> = {
  MONTHLY: 'monatlich',
  QUARTERLY: 'vierteljährlich',
  HALF_YEARLY: 'halbjährlich',
  YEARLY: 'jährlich',
}
