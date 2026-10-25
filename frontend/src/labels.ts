import type { CategoryGroup, RecurringInterval } from './api/types'

export const GROUP_LABELS: Record<CategoryGroup, string> = {
  FIXED: 'Fixkosten',
  FLEXIBLE: 'Flexible Ausgaben',
  SAVINGS: 'Sparen & Investieren',
}

export const UNGROUPED_LABEL = 'Ohne Gruppe'

export const INTERVAL_LABELS: Record<RecurringInterval, string> = {
  MONTHLY: 'monatlich',
  QUARTERLY: 'vierteljährlich',
  HALF_YEARLY: 'halbjährlich',
  YEARLY: 'jährlich',
}
